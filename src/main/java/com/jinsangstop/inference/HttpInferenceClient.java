package com.jinsangstop.inference;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 추론 서비스 HTTP 클라이언트 — `POST /v1/chat/completions`, `GET /health`.
 * llama-server 확장 인자 `id_slot`(슬롯 고정)과 `chat_template_kwargs.enable_thinking=false`(추론 모드 끔)를 함께 보낸다.
 * 주소·시간 제한은 환경변수(INFERENCE_SERVICE_URL 등)로 주입한다.
 */
@Component
public class HttpInferenceClient implements InferenceClient {

	private static final Logger log = LoggerFactory.getLogger(HttpInferenceClient.class);

	private final RestClient healthClient;
	private final RestClient completionClient;

	public HttpInferenceClient(
			@Value("${jinsangstop.inference.base-url}") String baseUrl,
			@Value("${jinsangstop.inference.connect-timeout}") Duration connectTimeout,
			@Value("${jinsangstop.inference.health-timeout}") Duration healthTimeout,
			@Value("${jinsangstop.inference.completion-timeout}") Duration completionTimeout) {
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(connectTimeout)
				.version(HttpClient.Version.HTTP_1_1)
				.build();
		this.healthClient = RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(requestFactory(httpClient, healthTimeout))
				.build();
		this.completionClient = RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(requestFactory(httpClient, completionTimeout))
				.build();
	}

	private static JdkClientHttpRequestFactory requestFactory(HttpClient httpClient, Duration readTimeout) {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(readTimeout);
		return factory;
	}

	/** llama-server `/health`는 모델 로딩이 끝나야 200이다(로딩 중 503). */
	@Override
	public boolean isReady() {
		try {
			return healthClient.get().uri("/health").retrieve().toBodilessEntity().getStatusCode().is2xxSuccessful();
		} catch (RestClientException e) {
			log.warn("추론 서비스 헬스 확인 실패: {}", e.getMessage());
			return false;
		}
	}

	@Override
	public String complete(ChatRequest request) {
		try {
			CompletionResponse response = completionClient.post()
					.uri("/v1/chat/completions")
					.contentType(MediaType.APPLICATION_JSON)
					.body(toBody(request))
					.retrieve()
					.onStatus(status -> status.isError(), (req, res) -> {
						throw new InferenceServiceException("추론 서비스 오류 응답: HTTP " + res.getStatusCode().value());
					})
					.body(CompletionResponse.class);
			if (response == null || response.choices() == null || response.choices().isEmpty()
					|| response.choices().getFirst().message() == null
					|| response.choices().getFirst().message().content() == null) {
				throw new InferenceServiceException("추론 서비스가 빈 응답을 돌려줬다");
			}
			return response.choices().getFirst().message().content();
		} catch (InferenceServiceException e) {
			throw e;
		} catch (RestClientException e) {
			throw new InferenceServiceException("추론 서비스 호출 실패: " + e.getMessage(), e);
		}
	}

	/** OpenAI 호환 요청 본문 + llama-server 확장 인자 */
	static Map<String, Object> toBody(ChatRequest request) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("messages", request.messages().stream()
				.map(m -> Map.of("role", m.role(), "content", m.content()))
				.toList());
		body.put("id_slot", request.slot());
		body.put("temperature", request.temperature());
		body.put("seed", request.seed());
		body.put("max_tokens", request.maxTokens());
		body.put("stream", false);
		body.put("cache_prompt", true);
		// 추론 모드를 끈다 — 켜면 대사 한 줄 전에 수백 토큰을 생성해 턴이 끊긴다(ADR-0009)
		body.put("chat_template_kwargs", Map.of("enable_thinking", false));
		if (request.schema() != null) {
			body.put("response_format", Map.of(
					"type", "json_schema",
					"json_schema", Map.of("name", request.schemaName(), "strict", true, "schema", request.schema())));
		}
		return body;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record CompletionResponse(List<Choice> choices) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Choice(ResponseMessage message) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ResponseMessage(String role, String content) {
	}
}
