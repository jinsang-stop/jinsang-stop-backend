package com.jinsangstop.training;

import com.jinsangstop.support.MutableClock;
import com.jinsangstop.support.TestDataCleaner;
import com.jinsangstop.voice.VoiceClient;
import com.jinsangstop.voice.VoiceServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 훈련 세션 시작·발화 턴 제출 API를 검증한다. 음성 서비스는 모의 객체로 대신한다.
 * 오디오 상한은 테스트에서 1KB로 낮춰 크기 초과를 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
		"jinsangstop.training.max-audio-bytes=1024",
		"jinsangstop.training.idle-timeout=10m"
})
class TrainingSessionApiTest {

	private static final Instant START = Instant.parse("2026-10-28T05:00:00.123456789Z");
	private static final String WEBM = "audio/webm;codecs=opus";
	private static final byte[] AUDIO = "fake-webm-audio-bytes".getBytes();

	/** 시각을 조절할 수 있는 시계로 바꿔 끼운다 */
	@TestConfiguration
	static class ClockTestConfig {
		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(START, ZoneId.of("Asia/Seoul"));
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private MutableClock clock;

	@MockitoBean
	private VoiceClient voiceClient;

	@BeforeEach
	void setUp() {
		TestDataCleaner.clearUserData(jdbcTemplate);
		clock.reset(START);
		when(voiceClient.isReady()).thenReturn(true);
	}

	// ── 세션 시작 ─────────────────────────────────────────

	@Test
	@DisplayName("카드와 강도로 세션을 시작하면 201과 세션 정보가 온다")
	void startSession() throws Exception {
		MockHttpSession user = signup("trainee01");

		startSession(user, 1, 2)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.sessionId").isNumber())
				.andExpect(jsonPath("$.cardId").value(1))
				.andExpect(jsonPath("$.complainantRoleName").value("손님"))
				.andExpect(jsonPath("$.intensity").value(2))
				.andExpect(jsonPath("$.hardTurnLimit").value(12))
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.turnCount").value(0))
				.andExpect(jsonPath("$.startedAt").value("2026-10-28T14:00:00"))
				.andExpect(jsonPath("$.calmingCondition").doesNotExist())
				.andExpect(jsonPath("$.nonNegotiableLine").doesNotExist());
	}

	@Test
	@DisplayName("강도를 생략하면 기본값 1로 시작한다")
	void defaultIntensity() throws Exception {
		MockHttpSession user = signup("trainee01");

		mockMvc.perform(post("/api/training-sessions").session(user)
						.contentType(MediaType.APPLICATION_JSON).content("{\"cardId\":1}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.intensity").value(1));
	}

	@Test
	@DisplayName("로그인하지 않으면 401 UNAUTHORIZED")
	void startRequiresLogin() throws Exception {
		mockMvc.perform(post("/api/training-sessions")
						.contentType(MediaType.APPLICATION_JSON).content("{\"cardId\":1,\"intensity\":1}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	@DisplayName("음성 서비스가 준비되지 않으면 503이고 세션이 만들어지지 않는다")
	void voiceNotReady() throws Exception {
		MockHttpSession user = signup("trainee01");
		when(voiceClient.isReady()).thenReturn(false);

		startSession(user, 1, 1)
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("TRAINING_ENVIRONMENT_NOT_READY"))
				.andExpect(jsonPath("$.message").value("훈련 환경이 준비되지 않았습니다. 잠시 후 다시 시도해 주세요."));

		assertThat(countSessions()).isZero();
	}

	@Test
	@DisplayName("다른 사람의 세션이 진행 중이면 409 TRAINING_SESSION_BUSY")
	void busyWhenAnotherSessionInProgress() throws Exception {
		startSession(signup("trainee01"), 1, 1).andExpect(status().isCreated());

		startSession(signup("trainee02"), 2, 1)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TRAINING_SESSION_BUSY"));
		assertThat(countSessions()).isEqualTo(1);
	}

	@Test
	@DisplayName("본인 세션이 진행 중이어도 새 세션은 409")
	void busyWhenOwnSessionInProgress() throws Exception {
		MockHttpSession user = signup("trainee01");
		startSession(user, 1, 1).andExpect(status().isCreated());

		startSession(user, 1, 1)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TRAINING_SESSION_BUSY"));
	}

	@Test
	@DisplayName("오래 방치된 세션은 정리되고 새 세션이 시작된다")
	void idleSessionIsAbandoned() throws Exception {
		long oldSessionId = sessionId(startSession(signup("trainee01"), 1, 1));
		clock.advance(Duration.ofMinutes(11));

		startSession(signup("trainee02"), 2, 1).andExpect(status().isCreated());

		assertThat(jdbcTemplate.queryForObject(
				"SELECT status FROM training_session WHERE id = ?", String.class, oldSessionId)).isEqualTo("ABANDONED");
	}

	@Test
	@DisplayName("방치 기준 시간 전이면 아직 진행 중으로 본다")
	void notIdleYet() throws Exception {
		startSession(signup("trainee01"), 1, 1);
		clock.advance(Duration.ofMinutes(9));

		startSession(signup("trainee02"), 2, 1).andExpect(status().isConflict());
	}

	@Test
	@DisplayName("강도가 범위 밖이면 400과 안내 메시지")
	void invalidIntensity() throws Exception {
		startSession(signup("trainee01"), 1, 4)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value("강도는 1~3 사이여야 합니다."));
	}

	@Test
	@DisplayName("없는 카드면 404 CARD_NOT_FOUND")
	void unknownCard() throws Exception {
		startSession(signup("trainee01"), 9999, 1)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CARD_NOT_FOUND"));
	}

	// ── 발화 턴 제출 ──────────────────────────────────────

	@Test
	@DisplayName("턴을 제출하면 전사가 턴 번호와 함께 저장되고 번호가 1씩 오른다")
	void submitTurns() throws Exception {
		MockHttpSession user = signup("trainee01");
		long sessionId = sessionId(startSession(user, 1, 1));
		when(voiceClient.transcribe(any(), anyString())).thenReturn(" 죄송합니다 손님 ", "영수증을 확인해 드릴게요");

		submitTurn(user, sessionId, AUDIO)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.sessionId").value(sessionId))
				.andExpect(jsonPath("$.turnNumber").value(1))
				.andExpect(jsonPath("$.userTranscript").value("죄송합니다 손님"));
		submitTurn(user, sessionId, AUDIO)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.turnNumber").value(2));

		verify(voiceClient, org.mockito.Mockito.times(2)).transcribe(eq(AUDIO), eq(WEBM));
		assertThat(jdbcTemplate.queryForList(
				"SELECT user_transcript FROM utterance_turn WHERE session_id = ? ORDER BY turn_number", String.class, sessionId))
				.containsExactly("죄송합니다 손님", "영수증을 확인해 드릴게요");
		assertThat(turnCount(sessionId)).isEqualTo(2);
	}

	@Test
	@DisplayName("빈 전사는 422이고 턴 번호와 turn_count를 소모하지 않는다")
	void emptyTranscriptDoesNotConsumeTurn() throws Exception {
		MockHttpSession user = signup("trainee01");
		long sessionId = sessionId(startSession(user, 1, 1));
		when(voiceClient.transcribe(any(), anyString())).thenReturn("", "   ", "죄송합니다 손님");

		submitTurn(user, sessionId, AUDIO)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("EMPTY_TRANSCRIPT"))
				.andExpect(jsonPath("$.message").value("말소리가 들리지 않았어요. 다시 말해 주세요."));
		submitTurn(user, sessionId, AUDIO)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("EMPTY_TRANSCRIPT"));

		assertThat(turnCount(sessionId)).isZero();
		assertThat(countTurns(sessionId)).isZero();

		// 빈 전사 두 번 뒤의 첫 정상 턴은 여전히 1번이다
		submitTurn(user, sessionId, AUDIO)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.turnNumber").value(1));
		assertThat(turnCount(sessionId)).isEqualTo(1);
	}

	@Test
	@DisplayName("본문이 비었으면 음성 서비스를 부르지 않고 422 EMPTY_TRANSCRIPT")
	void emptyBody() throws Exception {
		MockHttpSession user = signup("trainee01");
		long sessionId = sessionId(startSession(user, 1, 1));

		submitTurn(user, sessionId, new byte[0])
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("EMPTY_TRANSCRIPT"));
		verify(voiceClient, never()).transcribe(any(), anyString());
	}

	@Test
	@DisplayName("음성 서비스가 오디오를 읽지 못하면 422 AUDIO_UNREADABLE, 턴 미소모")
	void audioUnreadable() throws Exception {
		MockHttpSession user = signup("trainee01");
		long sessionId = sessionId(startSession(user, 1, 1));
		when(voiceClient.transcribe(any(), anyString()))
				.thenThrow(new VoiceServiceException(VoiceServiceException.Kind.AUDIO_UNREADABLE, "decode"));

		submitTurn(user, sessionId, AUDIO)
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("AUDIO_UNREADABLE"));
		assertThat(turnCount(sessionId)).isZero();
	}

	@Test
	@DisplayName("음성 서비스 장애면 503 TRAINING_ENVIRONMENT_NOT_READY, 턴 미소모")
	void voiceUnavailableDuringTurn() throws Exception {
		MockHttpSession user = signup("trainee01");
		long sessionId = sessionId(startSession(user, 1, 1));
		when(voiceClient.transcribe(any(), anyString()))
				.thenThrow(new VoiceServiceException(VoiceServiceException.Kind.UNAVAILABLE, "503"));

		submitTurn(user, sessionId, AUDIO)
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("TRAINING_ENVIRONMENT_NOT_READY"));
		assertThat(turnCount(sessionId)).isZero();
	}

	@Test
	@DisplayName("오디오가 상한을 넘으면 413 AUDIO_TOO_LARGE")
	void audioTooLarge() throws Exception {
		MockHttpSession user = signup("trainee01");
		long sessionId = sessionId(startSession(user, 1, 1));

		submitTurn(user, sessionId, new byte[1025])
				.andExpect(status().isContentTooLarge())
				.andExpect(jsonPath("$.code").value("AUDIO_TOO_LARGE"));
		verify(voiceClient, never()).transcribe(any(), anyString());
	}

	@Test
	@DisplayName("오디오가 아닌 Content-Type이면 415 UNSUPPORTED_MEDIA_TYPE")
	void notAudio() throws Exception {
		MockHttpSession user = signup("trainee01");
		long sessionId = sessionId(startSession(user, 1, 1));

		mockMvc.perform(post("/api/training-sessions/" + sessionId + "/turns").session(user)
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
	}

	@Test
	@DisplayName("남의 세션에 턴을 내면 404 TRAINING_SESSION_NOT_FOUND")
	void otherUsersSession() throws Exception {
		long sessionId = sessionId(startSession(signup("trainee01"), 1, 1));

		submitTurn(signup("trainee02"), sessionId, AUDIO)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TRAINING_SESSION_NOT_FOUND"));
		verify(voiceClient, never()).transcribe(any(), anyString());
	}

	@Test
	@DisplayName("정리된(끝난) 세션에 턴을 내면 409 TRAINING_SESSION_NOT_IN_PROGRESS")
	void abandonedSession() throws Exception {
		MockHttpSession user = signup("trainee01");
		long sessionId = sessionId(startSession(user, 1, 1));
		clock.advance(Duration.ofMinutes(11));
		startSession(signup("trainee02"), 1, 1).andExpect(status().isCreated());

		submitTurn(user, sessionId, AUDIO)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TRAINING_SESSION_NOT_IN_PROGRESS"));
	}

	@Test
	@DisplayName("턴 제출도 로그인이 필요하다")
	void submitRequiresLogin() throws Exception {
		mockMvc.perform(post("/api/training-sessions/1/turns").contentType(WEBM).content(AUDIO))
				.andExpect(status().isUnauthorized());
	}

	// ── 도우미 ───────────────────────────────────────────

	/** 가입(=로그인)하고 세션을 돌려준다. */
	private MockHttpSession signup(String loginId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
						.content("{\"loginId\":\"%s\",\"password\":\"password1234\",\"displayName\":\"테스터\"}".formatted(loginId)))
				.andExpect(status().isCreated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private ResultActions startSession(MockHttpSession user, long cardId, int intensity) throws Exception {
		return mockMvc.perform(post("/api/training-sessions").session(user)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"cardId\":%d,\"intensity\":%d}".formatted(cardId, intensity)));
	}

	private ResultActions submitTurn(MockHttpSession user, long sessionId, byte[] audio) throws Exception {
		return mockMvc.perform(post("/api/training-sessions/" + sessionId + "/turns").session(user)
				.contentType(WEBM).content(audio));
	}

	private long sessionId(ResultActions actions) throws Exception {
		String body = actions.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return Long.parseLong(body.replaceAll(".*\"sessionId\":(\\d+).*", "$1"));
	}

	private int countSessions() {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM training_session", Integer.class);
	}

	private int countTurns(long sessionId) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM utterance_turn WHERE session_id = ?", Integer.class, sessionId);
	}

	private int turnCount(long sessionId) {
		return jdbcTemplate.queryForObject("SELECT turn_count FROM training_session WHERE id = ?", Integer.class, sessionId);
	}
}
