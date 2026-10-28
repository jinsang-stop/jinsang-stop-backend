package com.jinsangstop.training;

import com.jinsangstop.support.TestDataCleaner;
import com.jinsangstop.inference.InferenceClient;
import com.jinsangstop.voice.VoiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * ADR-0006 — 턴 처리 후 서버 디스크에 오디오 파일이 남지 않는지 실제 HTTP 서버(Tomcat)로 확인한다.
 * Tomcat 작업 디렉터리를 전용 폴더로 돌려 두고, 2MB 오디오를 올린 뒤 그 폴더에 파일이 생기지 않았는지,
 * DB 어디에도 오디오 바이트가 없는지 본다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AudioNotPersistedTest {

	/** 이 테스트 전용 Tomcat 작업 디렉터리 */
	private static final Path TOMCAT_BASEDIR = createTempDir();

	@DynamicPropertySource
	static void tomcatBasedir(DynamicPropertyRegistry registry) {
		registry.add("server.tomcat.basedir", TOMCAT_BASEDIR::toString);
	}

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoBean
	private VoiceClient voiceClient;

	@MockitoBean
	private InferenceClient inferenceClient;

	private final HttpClient http = HttpClient.newHttpClient();

	@BeforeEach
	void setUp() {
		TestDataCleaner.clearUserData(jdbcTemplate);
		when(voiceClient.isReady()).thenReturn(true);
		when(voiceClient.transcribe(any(), anyString())).thenReturn("죄송합니다 손님");
		when(voiceClient.synthesize(anyString())).thenReturn(new byte[512 * 1024]);
		when(inferenceClient.isReady()).thenReturn(true);
		when(inferenceClient.complete(any())).thenReturn("{\"대사\": \"영수증부터 보여 드릴게요.\", \"진정함\": false}");
	}

	@Test
	@DisplayName("2MB 오디오로 턴을 처리한 뒤 Tomcat 작업 폴더에 파일이 없고 DB에는 전사만 남는다")
	void audioIsNotWrittenToDisk() throws Exception {
		String cookie = signupAndGetCookie();
		long sessionId = startSession(cookie);

		byte[] audio = new byte[2 * 1024 * 1024];
		for (int i = 0; i < audio.length; i++) {
			audio[i] = (byte) (i % 251);
		}
		HttpResponse<String> turn = http.send(HttpRequest.newBuilder(uri("/api/training-sessions/" + sessionId + "/turns"))
				.header("Cookie", cookie)
				.header("Content-Type", "audio/webm;codecs=opus")
				.POST(HttpRequest.BodyPublishers.ofByteArray(audio))
				.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(turn.statusCode()).isEqualTo(201);

		assertThat(regularFilesUnder(TOMCAT_BASEDIR)).isEmpty();

		// DB에 남는 것은 전사 텍스트뿐이다 — 턴 행의 모든 값이 짧은 텍스트·숫자다
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM utterance_turn WHERE session_id = ?", sessionId);
		assertThat(rows).hasSize(1);
		assertThat(rows.getFirst().get("user_transcript")).isEqualTo("죄송합니다 손님");
		assertThat(rows.getFirst().values()).allSatisfy(value -> assertThat(String.valueOf(value).length()).isLessThan(100));
	}

	private String signupAndGetCookie() throws Exception {
		HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/api/auth/signup"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(
						"{\"loginId\":\"disktest\",\"password\":\"password1234\",\"displayName\":\"테스터\"}"))
				.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(201);
		return response.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0];
	}

	private long startSession(String cookie) throws Exception {
		HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/api/training-sessions"))
				.header("Cookie", cookie)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString("{\"cardId\":1,\"intensity\":1}"))
				.build(), HttpResponse.BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(201);
		return Long.parseLong(response.body().replaceAll(".*\"sessionId\":(\\d+).*", "$1"));
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	private static List<Path> regularFilesUnder(Path root) throws IOException {
		try (Stream<Path> paths = Files.walk(root)) {
			return paths.filter(Files::isRegularFile).toList();
		}
	}

	private static Path createTempDir() {
		try {
			return Files.createTempDirectory("jinsangstop-tomcat-");
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
