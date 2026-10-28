package com.jinsangstop.training;

import com.jinsangstop.support.MutableClock;
import com.jinsangstop.support.TestDataCleaner;
import com.jinsangstop.inference.InferenceClient;
import com.jinsangstop.inference.InferenceServiceException;
import com.jinsangstop.scoring.AttitudeScorer;
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
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

	@MockitoBean
	private InferenceClient inferenceClient;

	/** 출력 검사를 통과하는 평범한 민원인 응답 */
	private static final String OK_REPLY = "{\"대사\": \"영수증이 없으면 어떡하라는 거예요?\", \"진정함\": false}";
	/** 유효한 채점 응답 */
	private static final String SCORE_JSON = """
			{"정서안정": {"점수": 4, "근거": "차분한 말투를 유지했다."},
			 "공감인정": {"점수": 5, "근거": "불편을 구체적으로 인정했다."},
			 "경계설정": {"점수": 2, "근거": "안 되는 것을 말하지 않았다."}}""";
	/** 채점 호출이 돌려줄 응답 — null이면 예외를 던진다 */
	private String scoreJson;
	/** 합성 음성 대신 쓸 바이트 */
	private static final byte[] SPEECH = "RIFF-fake-wav".getBytes();

	@BeforeEach
	void setUp() {
		TestDataCleaner.clearUserData(jdbcTemplate);
		clock.reset(START);
		when(voiceClient.isReady()).thenReturn(true);
		when(inferenceClient.isReady()).thenReturn(true);
		scoreJson = SCORE_JSON;
		stubActing(OK_REPLY);
		when(voiceClient.synthesize(anyString())).thenReturn(SPEECH);
		when(voiceClient.transcribe(any(), anyString())).thenReturn("죄송합니다 손님");
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
				.andExpect(jsonPath("$.userTranscript").value("죄송합니다 손님"))
				.andExpect(jsonPath("$.complainantLine").value("영수증이 없으면 어떡하라는 거예요?"))
				.andExpect(jsonPath("$.calmed").value(false))
				.andExpect(jsonPath("$.complainantAudio").value(Base64.getEncoder().encodeToString(SPEECH)))
				.andExpect(jsonPath("$.complainantAudioContentType").value("audio/wav"));
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

	// ── 민원인 응답(#4) ───────────────────────────────────

	@Test
	@DisplayName("추론 서비스가 준비되지 않으면 세션을 만들지 않고 503")
	void inferenceNotReady() throws Exception {
		MockHttpSession user = signup("trainee01");
		when(inferenceClient.isReady()).thenReturn(false);

		startSession(user, 1, 1)
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("TRAINING_ENVIRONMENT_NOT_READY"));
		assertThat(countSessions()).isZero();
	}

	@Test
	@DisplayName("연기 호출은 슬롯 0·temperature>0·세션 시드 기반이고, 이전 턴이 대화 기록으로 들어간다")
	void actingRequestShape() throws Exception {
		MockHttpSession user = signup("trainee01");
		long sessionId = sessionId(startSession(user, 1, 2));
		when(voiceClient.transcribe(any(), anyString())).thenReturn("어서 오세요", "영수증 있으세요?");

		submitTurn(user, sessionId, AUDIO).andExpect(status().isCreated());
		submitTurn(user, sessionId, AUDIO).andExpect(status().isCreated());

		ArgumentCaptor<InferenceClient.ChatRequest> captor = ArgumentCaptor.forClass(InferenceClient.ChatRequest.class);
		verify(inferenceClient, org.mockito.Mockito.atLeast(2)).complete(captor.capture());
		List<InferenceClient.ChatRequest> acting = captor.getAllValues().stream().filter(r -> r.slot() == 0).toList();
		assertThat(acting).hasSize(2);
		InferenceClient.ChatRequest second = acting.get(1);
		assertThat(second.slot()).isZero();
		assertThat(second.temperature()).isGreaterThan(0);
		assertThat(second.schema()).isNotNull();
		long seed = jdbcTemplate.queryForObject("SELECT acting_seed FROM training_session WHERE id = ?", Long.class, sessionId);
		assertThat(second.seed()).isEqualTo(seed + 2 * 10 + 1);
		// system + (직원1, 민원인1) + 직원2
		List<InferenceClient.Message> messages = second.messages();
		assertThat(messages).hasSize(4);
		assertThat(messages.get(0).content()).contains("강도 2단계", "언성을 높이고 반말을 섞는다").doesNotContain("고성으로 말을 몰아붙인다");
		assertThat(messages.get(1).content()).isEqualTo("직원: 어서 오세요");
		assertThat(messages.get(2).content()).contains("영수증이 없으면 어떡하라는 거예요?");
		assertThat(messages.get(3).content()).isEqualTo("직원: 영수증 있으세요?");
	}

	@Test
	@DisplayName("첫 대사가 금칙에 걸리면 재시도하고, 재시도 대사만 합성된다")
	void retryAfterForbiddenLine() throws Exception {
		MockHttpSession user = signup("trainee01");
		long sessionId = sessionId(startSession(user, 1, 1));
		stubActing(
				"{\"대사\": \"한 번만 더 그러면 죽여버린다\", \"진정함\": false}", OK_REPLY);

		submitTurn(user, sessionId, AUDIO)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.complainantLine").value("영수증이 없으면 어떡하라는 거예요?"));
		verify(voiceClient).synthesize("영수증이 없으면 어떡하라는 거예요?");
		verify(voiceClient, never()).synthesize(org.mockito.ArgumentMatchers.contains("죽여"));
	}

	@Test
	@DisplayName("금칙·비한국어 대사가 재시도까지 나오면 TTS로 넘기지 않고 세션을 시스템 오류로 끝낸다")
	void systemErrorAfterRetry() throws Exception {
		MockHttpSession user = signup("trainee01");
		long sessionId = sessionId(startSession(user, 1, 1));
		stubActing(
				"{\"대사\": \"죽 여 버 린 다\", \"진정함\": false}",
				"{\"대사\": \"你好，请给我退款\", \"진정함\": false}");

		submitTurn(user, sessionId, AUDIO)
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("SESSION_ENDED_BY_SYSTEM_ERROR"));

		verify(voiceClient, never()).synthesize(anyString());
		assertThat(jdbcTemplate.queryForObject("SELECT status FROM training_session WHERE id = ?", String.class, sessionId))
				.isEqualTo("SYSTEM_ERROR");
		assertThat(countTurns(sessionId)).isZero();
		// 잠금이 풀려 바로 새 세션을 시작할 수 있다
		startSession(user, 1, 1).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("형식이 깨진 응답도 출력 검사 실패로 보고 재시도한다")
	void malformedReplyRetries() throws Exception {
		MockHttpSession user = signup("trainee01");
		long sessionId = sessionId(startSession(user, 1, 1));
		stubActing("{\"대사\": \"},{\"", OK_REPLY);

		submitTurn(user, sessionId, AUDIO).andExpect(status().isCreated());
	}

	@Test
	@DisplayName("직원 말을 '직원:'째로 따라 한 응답은 역할 혼동으로 보고 재시도한다")
	void roleConfusionRetries() throws Exception {
		MockHttpSession user = signup("trainee01");
		long sessionId = sessionId(startSession(user, 1, 1));
		stubActing("{\"대사\": \"직원: 죄송합니다 손님\", \"진정함\": false}", OK_REPLY);

		submitTurn(user, sessionId, AUDIO)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.complainantLine").value("영수증이 없으면 어떡하라는 거예요?"));
		verify(voiceClient, never()).synthesize(org.mockito.ArgumentMatchers.startsWith("직원"));
	}

	@Test
	@DisplayName("추론 서비스 장애면 503이고 턴을 소모하지 않는다")
	void inferenceFailureDuringTurn() throws Exception {
		MockHttpSession user = signup("trainee01");
		long sessionId = sessionId(startSession(user, 1, 1));
		org.mockito.Mockito.doThrow(new InferenceServiceException("timeout")).when(inferenceClient).complete(any());

		submitTurn(user, sessionId, AUDIO)
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("TRAINING_ENVIRONMENT_NOT_READY"));
		assertThat(turnCount(sessionId)).isZero();
		assertThat(jdbcTemplate.queryForObject("SELECT status FROM training_session WHERE id = ?", String.class, sessionId))
				.isEqualTo("IN_PROGRESS");
	}

	@Test
	@DisplayName("합성 실패면 503이고 턴을 소모하지 않는다")
	void synthesisFailure() throws Exception {
		MockHttpSession user = signup("trainee01");
		long sessionId = sessionId(startSession(user, 1, 1));
		when(voiceClient.synthesize(anyString()))
				.thenThrow(new VoiceServiceException(VoiceServiceException.Kind.UNAVAILABLE, "TTS_FAILED"));

		submitTurn(user, sessionId, AUDIO)
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("TRAINING_ENVIRONMENT_NOT_READY"));
		assertThat(turnCount(sessionId)).isZero();
	}

	@Test
	@DisplayName("민원인 대사와 진정 여부가 턴과 함께 저장된다")
	void complainantLineStored() throws Exception {
		MockHttpSession user = signup("trainee01");
		long sessionId = sessionId(startSession(user, 1, 1));
		stubActing("{\"대사\": \"알겠어요, 고객센터로 접수할게요.\", \"진정함\": true}");

		submitTurn(user, sessionId, AUDIO)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.calmed").value(true));
		assertThat(jdbcTemplate.queryForMap("SELECT complainant_line, calmed FROM utterance_turn WHERE session_id = ?", sessionId))
				.containsEntry("complainant_line", "알겠어요, 고객센터로 접수할게요.")
				.containsEntry("calmed", true);
	}

	// ── 태도 3축 채점(#5) ─────────────────────────────────

	@Test
	@DisplayName("턴 응답에 세 축 점수와 근거가 실리고 합산 점수는 없다")
	void turnResponseHasScoresWithoutTotal() throws Exception {
		MockHttpSession user = signup("trainee01");
		long sessionId = sessionId(startSession(user, 1, 1));

		String body = submitTurn(user, sessionId, AUDIO)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.score.turnNumber").value(1))
				.andExpect(jsonPath("$.score.status").value("SCORED"))
				.andExpect(jsonPath("$.score.emotionalStability.score").value(4))
				.andExpect(jsonPath("$.score.emotionalStability.reason").value("차분한 말투를 유지했다."))
				.andExpect(jsonPath("$.score.empathyAcknowledgement.score").value(5))
				.andExpect(jsonPath("$.score.boundarySetting.score").value(2))
				.andReturn().getResponse().getContentAsString();

		assertThat(body.toLowerCase()).doesNotContain("total", "sum", "overall", "합산", "종합");
		assertThat(jdbcTemplate.queryForMap(
				"SELECT score_status, emotional_stability_score, empathy_acknowledgement_score, boundary_setting_score FROM utterance_turn WHERE session_id = ?",
				sessionId))
				.containsEntry("score_status", "SCORED")
				.containsEntry("emotional_stability_score", 4)
				.containsEntry("empathy_acknowledgement_score", 5)
				.containsEntry("boundary_setting_score", 2);
	}

	@Test
	@DisplayName("채점 호출은 슬롯 1·temperature 0·고정 시드이고, 카드의 숨은 조건과 이번 민원인 대사를 모른다")
	void scoringRequestShape() throws Exception {
		MockHttpSession user = signup("trainee01");
		long sessionId = sessionId(startSession(user, 1, 1));
		when(voiceClient.transcribe(any(), anyString())).thenReturn("어서 오세요", "영수증 있으세요?");

		submitTurn(user, sessionId, AUDIO).andExpect(status().isCreated());
		submitTurn(user, sessionId, AUDIO).andExpect(status().isCreated());

		ArgumentCaptor<InferenceClient.ChatRequest> captor = ArgumentCaptor.forClass(InferenceClient.ChatRequest.class);
		verify(inferenceClient, org.mockito.Mockito.atLeast(4)).complete(captor.capture());
		List<InferenceClient.ChatRequest> scoring = captor.getAllValues().stream().filter(r -> r.slot() == 1).toList();
		assertThat(scoring).hasSize(2);
		assertThat(scoring).allSatisfy(r -> {
			assertThat(r.temperature()).isZero();
			assertThat(r.seed()).isEqualTo(42);
			String all = r.messages().stream().map(InferenceClient.Message::content).reduce("", String::concat);
			assertThat(all).doesNotContain("구매 내역(영수증 또는 카드 결제 조회)을 확인하지 않고", "확인 가능한 대안을 분명하게 제시하면 진정한다");
		});
		// 첫 턴은 직전 민원인 말이 없고, 둘째 턴은 첫 턴의 민원인 대사를 맥락으로 받는다
		assertThat(scoring.get(0).messages().getLast().content()).contains("대화 시작", "어서 오세요");
		assertThat(scoring.get(1).messages().getLast().content()).contains("영수증이 없으면 어떡하라는 거예요?", "영수증 있으세요?");
	}

	@Test
	@DisplayName("채점 응답이 범위를 벗어나면 0점이 아니라 정보 없음(FAILED, NULL)으로 저장된다")
	void invalidScoreStoredAsUnknown() throws Exception {
		MockHttpSession user = signup("trainee01");
		long sessionId = sessionId(startSession(user, 1, 1));
		scoreJson = SCORE_JSON.replace("\"점수\": 4", "\"점수\": 7");

		submitTurn(user, sessionId, AUDIO)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.score.status").value("FAILED"))
				.andExpect(jsonPath("$.score.emotionalStability").doesNotExist())
				.andExpect(jsonPath("$.score.boundarySetting").doesNotExist());

		var row = jdbcTemplate.queryForMap(
				"SELECT score_status, emotional_stability_score, empathy_acknowledgement_score, boundary_setting_score FROM utterance_turn WHERE session_id = ?",
				sessionId);
		assertThat(row.get("score_status")).isEqualTo("FAILED");
		assertThat(row.get("emotional_stability_score")).isNull();
		assertThat(row.get("empathy_acknowledgement_score")).isNull();
		assertThat(row.get("boundary_setting_score")).isNull();
	}

	@Test
	@DisplayName("채점 호출이 실패해도 턴은 진행되고 점수는 정보 없음이다")
	void scoringFailureDoesNotBlockTurn() throws Exception {
		MockHttpSession user = signup("trainee01");
		long sessionId = sessionId(startSession(user, 1, 1));
		scoreJson = null; // 채점 호출이 예외를 던지게 한다

		submitTurn(user, sessionId, AUDIO)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.turnNumber").value(1))
				.andExpect(jsonPath("$.score.status").value("FAILED"));
	}

	@Test
	@DisplayName("점수 조회 API — 본인 턴은 200, 없는 턴은 404 TURN_NOT_FOUND, 남의 세션은 404")
	void getTurnScore() throws Exception {
		MockHttpSession user = signup("trainee01");
		long sessionId = sessionId(startSession(user, 1, 1));
		submitTurn(user, sessionId, AUDIO).andExpect(status().isCreated());

		mockMvc.perform(get("/api/training-sessions/" + sessionId + "/turns/1/score").session(user))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SCORED"))
				.andExpect(jsonPath("$.empathyAcknowledgement.score").value(5));
		mockMvc.perform(get("/api/training-sessions/" + sessionId + "/turns/9/score").session(user))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TURN_NOT_FOUND"));
		mockMvc.perform(get("/api/training-sessions/" + sessionId + "/turns/1/score").session(signup("trainee02")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TRAINING_SESSION_NOT_FOUND"));
	}

	// ── 도우미 ───────────────────────────────────────────

	/**
	 * 추론 서비스 모의 응답을 슬롯으로 나눈다 — 슬롯 1(채점)은 scoreJson, 슬롯 0(연기)은 replies를 차례로(마지막 값 반복).
	 * 연기와 채점이 병렬로 불리므로 호출 순서에 기대지 않게 하기 위함이다.
	 */
	private void stubActing(String... replies) {
		java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger();
		// when(...)은 기존 응답을 null 인자로 한 번 실행하므로 doAnswer로 건다
		org.mockito.Mockito.doAnswer(invocation -> {
			InferenceClient.ChatRequest request = invocation.getArgument(0);
			if (request.slot() == AttitudeScorer.SCORING_SLOT) {
				if (scoreJson == null) {
					throw new InferenceServiceException("채점 호출 실패(모의)");
				}
				return scoreJson;
			}
			int index = next.getAndIncrement();
			return replies[Math.min(index, replies.length - 1)];
		}).when(inferenceClient).complete(any());
	}

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
