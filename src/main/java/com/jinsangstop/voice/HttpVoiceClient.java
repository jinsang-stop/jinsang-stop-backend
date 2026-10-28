package com.jinsangstop.voice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * 음성 서비스 HTTP 클라이언트.
 * 주소와 시간 제한은 환경변수(VOICE_SERVICE_URL 등)로 주입한다 — 코드에 박지 않는다.
 * 오디오는 multipart가 아니라 요청 본문에 원시 바이트로 보낸다(음성 서비스가 디스크에 쓰지 않게 하려는 계약).
 */
@Component
public class HttpVoiceClient implements VoiceClient {

	private static final Logger log = LoggerFactory.getLogger(HttpVoiceClient.class);

	/** 헬스 확인용 — 짧은 시간 제한 */
	private final RestClient healthClient;
	/** 전사·합성용 — 긴 발화를 처리할 만큼의 시간 제한 */
	private final RestClient transcribeClient;

	public HttpVoiceClient(
			@Value("${jinsangstop.voice.base-url}") String baseUrl,
			@Value("${jinsangstop.voice.connect-timeout}") Duration connectTimeout,
			@Value("${jinsangstop.voice.health-timeout}") Duration healthTimeout,
			@Value("${jinsangstop.voice.transcribe-timeout}") Duration transcribeTimeout) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(connectTimeout)
				// 음성 서비스는 uvicorn(HTTP/1.1) — 업그레이드 협상 없이 바로 1.1로 보낸다
				.version(HttpClient.Version.HTTP_1_1)
				.build();
		this.healthClient = RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(requestFactory(httpClient, healthTimeout))
				.build();
		this.transcribeClient = RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(requestFactory(httpClient, transcribeTimeout))
				.build();
	}

	/** 읽기 시간 제한을 건 요청 팩토리 */
	private static JdkClientHttpRequestFactory requestFactory(HttpClient httpClient, Duration readTimeout) {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(readTimeout);
		return factory;
	}

	/** GET /health 가 200이면 준비된 것으로 본다(전사·합성 모두 올라왔을 때만 200). */
	@Override
	public boolean isReady() {
		try {
			return healthClient.get()
					.uri("/health")
					.retrieve()
					.toBodilessEntity()
					.getStatusCode()
					.is2xxSuccessful();
		} catch (RestClientException e) {
			log.warn("음성 서비스 헬스 확인 실패: {}", e.getMessage());
			return false;
		}
	}

	/** POST /transcribe — 본문에 오디오 바이트를 그대로 싣는다. */
	@Override
	public String transcribe(byte[] audio, String contentType) {
		try {
			TranscribeResponse response = transcribeClient.post()
					.uri("/transcribe")
					.contentType(MediaType.parseMediaType(contentType))
					.body(audio)
					.retrieve()
					.onStatus(status -> status.isError(), (request, res) -> {
						int code = res.getStatusCode().value();
						// 422는 보낸 쪽(오디오) 문제, 413은 크기 초과, 나머지(503 등)는 다시 말해도 풀리지 않는 환경 문제
						throw switch (code) {
							case 422 -> new VoiceServiceException(VoiceServiceException.Kind.AUDIO_UNREADABLE,
									"음성 서비스가 오디오를 읽지 못함");
							case 413 -> new VoiceServiceException(VoiceServiceException.Kind.AUDIO_TOO_LARGE,
									"오디오 크기 초과");
							default -> new VoiceServiceException(VoiceServiceException.Kind.UNAVAILABLE,
									"음성 서비스 전사 실패: HTTP " + code);
						};
					})
					.body(TranscribeResponse.class);
			return response == null || response.text() == null ? "" : response.text();
		} catch (VoiceServiceException e) {
			throw e;
		} catch (ResourceAccessException e) {
			// 연결 거부·시간 초과
			throw new VoiceServiceException(VoiceServiceException.Kind.UNAVAILABLE, "음성 서비스에 연결하지 못함", e);
		} catch (RestClientException e) {
			throw new VoiceServiceException(VoiceServiceException.Kind.UNAVAILABLE, "음성 서비스 응답 처리 실패", e);
		}
	}

	/** POST /synthesize — 응답 본문은 JSON이 아니라 audio/wav 바이트다. */
	@Override
	public byte[] synthesize(String text) {
		try {
			byte[] audio = transcribeClient.post()
					.uri("/synthesize")
					.contentType(MediaType.APPLICATION_JSON)
					.body(Map.of("text", text))
					.retrieve()
					.onStatus(status -> status.isError(), (request, res) -> {
						// 합성 실패는 음성 서비스가 422를 두지 않고 전부 자기 쪽 문제로 보고한다(음성 저장소 README)
						throw new VoiceServiceException(VoiceServiceException.Kind.UNAVAILABLE,
								"음성 서비스 합성 실패: HTTP " + res.getStatusCode().value());
					})
					.body(byte[].class);
			if (audio == null || audio.length == 0) {
				throw new VoiceServiceException(VoiceServiceException.Kind.UNAVAILABLE, "음성 서비스가 빈 오디오를 돌려줬다");
			}
			return audio;
		} catch (VoiceServiceException e) {
			throw e;
		} catch (RestClientException e) {
			throw new VoiceServiceException(VoiceServiceException.Kind.UNAVAILABLE, "음성 서비스 합성 호출 실패", e);
		}
	}

	/** 음성 서비스 전사 응답 — 백엔드는 text만 쓰고 나머지 측정값은 무시한다 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record TranscribeResponse(String text) {
	}
}
