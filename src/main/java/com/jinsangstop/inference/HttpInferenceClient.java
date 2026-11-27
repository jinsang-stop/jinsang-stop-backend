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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 추론 서비스 HTTP 클라이언트 — `POST /v1/chat/completions`, `GET /health`, 캐시 예열용 `POST /apply-template`·`POST /completion`.
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

	/** 접두사를 얻으려고 붙이는 서로 다른 두 사용자 메시지 — 첫 글자가 달라야 한다 */
	private static final String PROBE_A = "가";
	private static final String PROBE_B = "나";

	/**
	 * llama-server의 채팅 템플릿으로 {@code prefix + 사용자 메시지}를 두 번 렌더링해 공통 앞부분을 구하고,
	 * 그 문자열만 `/completion`(생성 0토큰)으로 보내 슬롯에 캐시한다.
	 * 템플릿 표지를 코드에 박지 않으려고 두 렌더링의 공통 접두사를 쓴다 — 모델을 바꿔도 그대로 맞는다.
	 */
	@Override
	public void warmup(List<Message> prefix, int slot) {
		String prompt = commonPrefix(renderWithUser(prefix, PROBE_A), renderWithUser(prefix, PROBE_B));
		if (prompt.isEmpty()) {
			throw new InferenceServiceException("예열 접두사를 만들지 못했다");
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("prompt", prompt);
		body.put("id_slot", slot);
		body.put("n_predict", 0);
		body.put("cache_prompt", true);
		try {
			completionClient.post()
					.uri("/completion")
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.onStatus(status -> status.isError(), (req, res) -> {
						throw new InferenceServiceException("추론 서비스 오류 응답: HTTP " + res.getStatusCode().value());
					})
					.toBodilessEntity();
		} catch (InferenceServiceException e) {
			throw e;
		} catch (RestClientException e) {
			throw new InferenceServiceException("추론 서비스 예열 실패: " + e.getMessage(), e);
		}
	}

	/** `POST /apply-template` — 생성 없이 채팅 템플릿만 적용한 프롬프트 문자열 */
	private String renderWithUser(List<Message> prefix, String userContent) {
		List<Message> messages = new ArrayList<>(prefix);
		messages.add(Message.user(userContent));
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("messages", toMessages(messages));
		body.put("chat_template_kwargs", Map.of("enable_thinking", false));
		try {
			TemplateResponse response = completionClient.post()
					.uri("/apply-template")
					.contentType(MediaType.APPLICATION_JSON)
					.body(body)
					.retrieve()
					.onStatus(status -> status.isError(), (req, res) -> {
						throw new InferenceServiceException("추론 서비스 오류 응답: HTTP " + res.getStatusCode().value());
					})
					.body(TemplateResponse.class);
			if (response == null || response.prompt() == null) {
				throw new InferenceServiceException("추론 서비스가 빈 템플릿을 돌려줬다");
			}
			return response.prompt();
		} catch (InferenceServiceException e) {
			throw e;
		} catch (RestClientException e) {
			throw new InferenceServiceException("추론 서비스 템플릿 적용 실패: " + e.getMessage(), e);
		}
	}

	static String commonPrefix(String a, String b) {
		int length = Math.min(a.length(), b.length());
		int i = 0;
		while (i < length && a.charAt(i) == b.charAt(i)) {
			i++;
		}
		// 서로게이트 쌍 한가운데서 자르지 않는다
		if (i > 0 && Character.isHighSurrogate(a.charAt(i - 1))) {
			i--;
		}
		return a.substring(0, i);
	}

	private static List<Map<String, String>> toMessages(List<Message> messages) {
		return messages.stream()
				.map(m -> Map.of("role", m.role(), "content", m.content()))
				.toList();
	}

	/** OpenAI 호환 요청 본문 + llama-server 확장 인자 */
	static Map<String, Object> toBody(ChatRequest request) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("messages", toMessages(request.messages()));
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
	record TemplateResponse(String prompt) {
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
