package com.jinsangstop.voice;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 음성 서비스 HTTP 계약 검증 — 가짜 음성 서비스(JDK HttpServer)를 띄워
 * 요청 형식(원시 바이트 본문·Content-Type)과 상태 코드별 처리(422 → 다시 말하기, 503 → 환경 문제)를 확인한다.
 */
class HttpVoiceClientTest {

	private HttpServer server;
	private HttpVoiceClient client;

	/** 가짜 서버가 돌려줄 상태·본문 */
	private int transcribeStatus;
	private String transcribeBody;
	private int healthStatus;

	/** 가짜 서버가 받은 요청 */
	private final AtomicReference<byte[]> receivedBody = new AtomicReference<>();
	private final AtomicReference<String> receivedContentType = new AtomicReference<>();

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/health", exchange -> respond(exchange, healthStatus, "{\"status\":\"ok\"}"));
		server.createContext("/transcribe", exchange -> {
			receivedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
			receivedBody.set(exchange.getRequestBody().readAllBytes());
			respond(exchange, transcribeStatus, transcribeBody);
		});
		server.start();
		client = clientFor("http://127.0.0.1:" + server.getAddress().getPort());
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	@DisplayName("헬스가 200이면 준비됨, 503이면 준비 안 됨")
	void health() {
		healthStatus = 200;
		assertThat(client.isReady()).isTrue();

		healthStatus = 503;
		assertThat(client.isReady()).isFalse();
	}

	@Test
	@DisplayName("음성 서비스가 꺼져 있으면 준비 안 됨, 전사는 UNAVAILABLE")
	void serverDown() throws IOException {
		HttpVoiceClient downClient = clientFor("http://127.0.0.1:" + unusedPort());

		assertThat(downClient.isReady()).isFalse();
		assertThatThrownBy(() -> downClient.transcribe(new byte[]{1}, "audio/webm"))
				.isInstanceOfSatisfying(VoiceServiceException.class,
						e -> assertThat(e.getKind()).isEqualTo(VoiceServiceException.Kind.UNAVAILABLE));
	}

	@Test
	@DisplayName("오디오는 원시 바이트 본문과 원래 Content-Type 그대로 보내고 text만 꺼낸다")
	void transcribeSendsRawBytes() {
		transcribeStatus = 200;
		transcribeBody = "{\"text\":\"죄송합니다 손님\",\"audio_seconds\":1.2,\"processing_seconds\":0.8}";
		byte[] audio = {10, 20, 30, 40};

		String text = client.transcribe(audio, "audio/webm;codecs=opus");

		assertThat(text).isEqualTo("죄송합니다 손님");
		assertThat(receivedBody.get()).isEqualTo(audio);
		assertThat(receivedContentType.get()).startsWith("audio/webm");
	}

	@Test
	@DisplayName("말소리가 없으면 빈 문자열(200)을 그대로 돌려준다")
	void emptyText() {
		transcribeStatus = 200;
		transcribeBody = "{\"text\":\"\",\"audio_seconds\":1.0,\"processing_seconds\":0.5}";

		assertThat(client.transcribe(new byte[]{1}, "audio/webm")).isEmpty();
	}

	@Test
	@DisplayName("422는 AUDIO_UNREADABLE(다시 말하기)")
	void decodeFailed() {
		transcribeStatus = 422;
		transcribeBody = "{\"error_code\":\"AUDIO_DECODE_FAILED\"}";

		assertKind(VoiceServiceException.Kind.AUDIO_UNREADABLE);
	}

	@Test
	@DisplayName("413은 AUDIO_TOO_LARGE")
	void tooLarge() {
		transcribeStatus = 413;
		transcribeBody = "{\"error_code\":\"AUDIO_TOO_LARGE\"}";

		assertKind(VoiceServiceException.Kind.AUDIO_TOO_LARGE);
	}

	@Test
	@DisplayName("503(모델 미준비·전사 실패)은 UNAVAILABLE(환경 문제)")
	void serviceUnavailable() {
		transcribeStatus = 503;
		transcribeBody = "{\"error_code\":\"STT_FAILED\"}";

		assertKind(VoiceServiceException.Kind.UNAVAILABLE);
	}

	private void assertKind(VoiceServiceException.Kind kind) {
		assertThatThrownBy(() -> client.transcribe(new byte[]{1}, "audio/webm"))
				.isInstanceOfSatisfying(VoiceServiceException.class, e -> assertThat(e.getKind()).isEqualTo(kind));
	}

	private static HttpVoiceClient clientFor(String baseUrl) {
		return new HttpVoiceClient(baseUrl, Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(5));
	}

	private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json");
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

	/** 아무도 듣지 않는 포트 하나를 고른다. */
	private static int unusedPort() throws IOException {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		}
	}
}
