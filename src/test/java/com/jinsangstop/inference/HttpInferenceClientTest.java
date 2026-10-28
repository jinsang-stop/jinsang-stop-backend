package com.jinsangstop.inference;

import com.jinsangstop.inference.InferenceClient.ChatRequest;
import com.jinsangstop.inference.InferenceClient.Message;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 추론 서비스 HTTP 계약 검증 — 가짜 llama-server를 띄워 요청 본문(슬롯·추론 모드 끔·JSON 스키마)과 응답 처리를 확인한다.
 */
class HttpInferenceClientTest {

	private final JsonMapper jsonMapper = JsonMapper.builder().build();
	private HttpServer server;
	private HttpInferenceClient client;
	private int completionStatus;
	private String completionBody;
	private int healthStatus;
	private final AtomicReference<String> receivedBody = new AtomicReference<>();

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/health", exchange -> respond(exchange, healthStatus, "{\"status\":\"ok\"}"));
		server.createContext("/v1/chat/completions", exchange -> {
			receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			respond(exchange, completionStatus, completionBody);
		});
		server.start();
		client = clientFor("http://127.0.0.1:" + server.getAddress().getPort());
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	@DisplayName("요청 본문에 id_slot·temperature·seed·추론 모드 끔·JSON 스키마가 실린다")
	void requestBody() {
		completionStatus = 200;
		completionBody = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"대사\\\":\\\"네?\\\",\\\"진정함\\\":false}\"}}]}";
		Map<String, Object> schema = Map.of("type", "object");

		String content = client.complete(new ChatRequest(
				List.of(Message.system("시스템"), Message.user("직원: 안녕하세요")), 1, 0.0, 42, 120, "attitude_score", schema));

		assertThat(content).isEqualTo("{\"대사\":\"네?\",\"진정함\":false}");
		JsonNode body = jsonMapper.readTree(receivedBody.get());
		assertThat(body.get("id_slot").asInt()).isEqualTo(1);
		assertThat(body.get("temperature").asDouble()).isZero();
		assertThat(body.get("seed").asLong()).isEqualTo(42);
		assertThat(body.get("max_tokens").asInt()).isEqualTo(120);
		assertThat(body.get("chat_template_kwargs").get("enable_thinking").asBoolean()).isFalse();
		assertThat(body.get("response_format").get("type").asString()).isEqualTo("json_schema");
		assertThat(body.get("response_format").get("json_schema").get("name").asString()).isEqualTo("attitude_score");
		assertThat(body.get("response_format").get("json_schema").get("strict").asBoolean()).isTrue();
		assertThat(body.get("messages").get(1).get("content").asString()).isEqualTo("직원: 안녕하세요");
	}

	@Test
	@DisplayName("스키마가 없으면 response_format을 보내지 않는다(캐시 예열)")
	void noSchema() {
		completionStatus = 200;
		completionBody = "{\"choices\":[{\"message\":{\"content\":\"네\"}}]}";

		client.complete(new ChatRequest(List.of(Message.system("시스템")), 0, 0.8, 7, 1, null, null));

		assertThat(jsonMapper.readTree(receivedBody.get()).has("response_format")).isFalse();
	}

	@Test
	@DisplayName("오류 응답·빈 응답·연결 거부는 InferenceServiceException")
	void failures() throws IOException {
		completionStatus = 503;
		completionBody = "{\"error\":\"loading\"}";
		assertThatThrownBy(() -> client.complete(simpleRequest())).isInstanceOf(InferenceServiceException.class);

		completionStatus = 200;
		completionBody = "{\"choices\":[]}";
		assertThatThrownBy(() -> client.complete(simpleRequest())).isInstanceOf(InferenceServiceException.class);

		HttpInferenceClient down = clientFor("http://127.0.0.1:" + unusedPort());
		assertThatThrownBy(() -> down.complete(simpleRequest())).isInstanceOf(InferenceServiceException.class);
		assertThat(down.isReady()).isFalse();
	}

	@Test
	@DisplayName("헬스가 200이면 준비됨, 503(모델 로딩 중)이면 준비 안 됨")
	void health() {
		healthStatus = 200;
		assertThat(client.isReady()).isTrue();
		healthStatus = 503;
		assertThat(client.isReady()).isFalse();
	}

	private static ChatRequest simpleRequest() {
		return new ChatRequest(List.of(Message.user("안녕")), 0, 0.8, 1, 10, null, null);
	}

	private static HttpInferenceClient clientFor(String baseUrl) {
		return new HttpInferenceClient(baseUrl, Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(5));
	}

	private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

	private static int unusedPort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}
}
