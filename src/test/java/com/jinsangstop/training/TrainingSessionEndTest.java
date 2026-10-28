package com.jinsangstop.training;

import com.jinsangstop.inference.InferenceClient;
import com.jinsangstop.scoring.AttitudeScorer;
import com.jinsangstop.support.TestDataCleaner;
import com.jinsangstop.voice.VoiceClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 종료 판정 테스트 — 진정 종료·하드 턴 상한·사용자 중단 각각의 종료 사유와 소요 턴 수, 종료 후 턴 거절과 잠금 해제.
 * 하드 턴 상한은 상한 3짜리 테스트 카드를 넣어 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TrainingSessionEndTest {

	private static final String CALM = "{\"대사\": \"알겠어요, 고객센터로 접수해 주세요.\", \"진정함\": true}";
	private static final String NOT_CALM = "{\"대사\": \"그래서 환불은 언제 해 준다는 거예요?\", \"진정함\": false}";
	private static final String SCORE = """
			{"정서안정": {"점수": 4, "근거": "차분했다."}, "공감인정": {"점수": 4, "근거": "인정했다."}, "경계설정": {"점수": 4, "근거": "분명했다."}}""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoBean
	private VoiceClient voiceClient;

	@MockitoBean
	private InferenceClient inferenceClient;

	/** 연기 응답이 진정했는지 — 테스트마다 바꾼다 */
	private final AtomicBoolean calmNext = new AtomicBoolean(false);
	/** 상한 3짜리 테스트 카드 */
	private long smallCardId;

	@BeforeEach
	void setUp() {
		TestDataCleaner.clearUserData(jdbcTemplate);
		jdbcTemplate.update("""
				INSERT INTO scenario_card (card_type, name, situation, complainant_role_name, complainant_persona,
				  complainant_demand, non_negotiable_line, calming_condition, hard_turn_limit)
				VALUES ('OFFICIAL', '테스트 상한 카드', '테스트 상황', '손님', '테스트 성격', '테스트 요구', '테스트 선', '테스트 조건', 3)""");
		smallCardId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM scenario_card", Long.class);
		calmNext.set(false);
		when(voiceClient.isReady()).thenReturn(true);
		when(inferenceClient.isReady()).thenReturn(true);
		when(voiceClient.transcribe(any(), anyString())).thenReturn("죄송합니다 손님");
		when(voiceClient.synthesize(anyString())).thenReturn("RIFF".getBytes());
		doAnswer(invocation -> {
			InferenceClient.ChatRequest request = invocation.getArgument(0);
			if (request.slot() == AttitudeScorer.SCORING_SLOT) {
				return SCORE;
			}
			return calmNext.get() ? CALM : NOT_CALM;
		}).when(inferenceClient).complete(any());
	}

	@AfterEach
	void removeTestCard() {
		TestDataCleaner.clearUserData(jdbcTemplate);
		jdbcTemplate.update("DELETE FROM scenario_card WHERE id = ?", smallCardId);
	}

	@Test
	@DisplayName("진정 여부가 참인 턴에서 진정 종료(CALMED)와 소요 턴 수가 저장된다")
	void calmedEndsWithTurnsToCalm() throws Exception {
		MockHttpSession user = signup("end01");
		long sessionId = start(user, 1);

		submitTurn(user, sessionId).andExpect(jsonPath("$.sessionStatus").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.turnsToCalm").doesNotExist());
		calmNext.set(true);
		submitTurn(user, sessionId)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.turnNumber").value(2))
				.andExpect(jsonPath("$.calmed").value(true))
				.andExpect(jsonPath("$.sessionStatus").value("CALMED"))
				.andExpect(jsonPath("$.turnsToCalm").value(2));

		Map<String, Object> row = sessionRow(sessionId);
		assertThat(row.get("status")).isEqualTo("CALMED");
		assertThat(row.get("turns_to_calm")).isEqualTo(2);
		assertThat(row.get("active_slot")).isNull();
		assertThat(row.get("ended_at")).isNotNull();
	}

	@Test
	@DisplayName("하드 턴 상한에 닿으면 TURN_LIMIT_REACHED이고 소요 턴 수는 기록되지 않는다")
	void turnLimitReached() throws Exception {
		MockHttpSession user = signup("end01");
		long sessionId = start(user, smallCardId);

		submitTurn(user, sessionId).andExpect(jsonPath("$.sessionStatus").value("IN_PROGRESS"));
		submitTurn(user, sessionId).andExpect(jsonPath("$.sessionStatus").value("IN_PROGRESS"));
		submitTurn(user, sessionId)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.turnNumber").value(3))
				.andExpect(jsonPath("$.sessionStatus").value("TURN_LIMIT_REACHED"))
				.andExpect(jsonPath("$.turnsToCalm").doesNotExist());

		Map<String, Object> row = sessionRow(sessionId);
		assertThat(row.get("status")).isEqualTo("TURN_LIMIT_REACHED");
		assertThat(row.get("turns_to_calm")).isNull();
		assertThat(row.get("active_slot")).isNull();
	}

	@Test
	@DisplayName("마지막 턴에서 진정하면 상한보다 진정 종료가 우선한다")
	void calmedOnLastTurnWins() throws Exception {
		MockHttpSession user = signup("end01");
		long sessionId = start(user, smallCardId);
		submitTurn(user, sessionId);
		submitTurn(user, sessionId);
		calmNext.set(true);

		submitTurn(user, sessionId)
				.andExpect(jsonPath("$.sessionStatus").value("CALMED"))
				.andExpect(jsonPath("$.turnsToCalm").value(3));
	}

	@Test
	@DisplayName("중단하면 ABORTED, 소요 턴 수 없음, 바로 새 세션을 시작할 수 있다")
	void abort() throws Exception {
		MockHttpSession user = signup("end01");
		long sessionId = start(user, 1);
		submitTurn(user, sessionId);

		mockMvc.perform(post("/api/training-sessions/" + sessionId + "/abort").session(user))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ABORTED"))
				.andExpect(jsonPath("$.turnCount").value(1))
				.andExpect(jsonPath("$.turnsToCalm").doesNotExist())
				.andExpect(jsonPath("$.endedAt").isNotEmpty());

		assertThat(sessionRow(sessionId).get("turns_to_calm")).isNull();
		start(user, 1);
	}

	@Test
	@DisplayName("종료된 세션에 턴을 내면 409이고, 중단을 다시 불러도 409")
	void endedSessionRejects() throws Exception {
		MockHttpSession user = signup("end01");
		long sessionId = start(user, 1);
		calmNext.set(true);
		submitTurn(user, sessionId).andExpect(jsonPath("$.sessionStatus").value("CALMED"));

		submitTurn(user, sessionId)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TRAINING_SESSION_NOT_IN_PROGRESS"));
		mockMvc.perform(post("/api/training-sessions/" + sessionId + "/abort").session(user))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TRAINING_SESSION_NOT_IN_PROGRESS"));
		// 진정 종료로 잠금이 풀려 다른 사용자가 바로 시작할 수 있다
		start(signup("end02"), 1);
	}

	@Test
	@DisplayName("남의 세션은 중단할 수 없다(404)")
	void abortOtherUsersSession() throws Exception {
		long sessionId = start(signup("end01"), 1);

		mockMvc.perform(post("/api/training-sessions/" + sessionId + "/abort").session(signup("end02")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("TRAINING_SESSION_NOT_FOUND"));
		assertThat(sessionRow(sessionId).get("status")).isEqualTo("IN_PROGRESS");
	}

	@Test
	@DisplayName("처리 중이던 턴은 중단되면 결과가 버려진다")
	void inFlightTurnDiscardedOnAbort() throws Exception {
		MockHttpSession user = signup("end01");
		long sessionId = start(user, 1);
		CountDownLatch actingStarted = new CountDownLatch(1);
		CountDownLatch releaseActing = new CountDownLatch(1);
		doAnswer(invocation -> {
			InferenceClient.ChatRequest request = invocation.getArgument(0);
			if (request.slot() == AttitudeScorer.SCORING_SLOT) {
				return SCORE;
			}
			actingStarted.countDown();
			releaseActing.await(10, TimeUnit.SECONDS);
			return NOT_CALM;
		}).when(inferenceClient).complete(any());

		CompletableFuture<MvcResult> turn = CompletableFuture.supplyAsync(() -> {
			try {
				return mockMvc.perform(post("/api/training-sessions/" + sessionId + "/turns").session(user)
						.contentType("audio/webm").content(new byte[]{1, 2, 3})).andReturn();
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		});
		assertThat(actingStarted.await(10, TimeUnit.SECONDS)).isTrue();
		mockMvc.perform(post("/api/training-sessions/" + sessionId + "/abort").session(user))
				.andExpect(status().isOk());
		releaseActing.countDown();

		MvcResult result = turn.get(15, TimeUnit.SECONDS);
		assertThat(result.getResponse().getStatus()).isEqualTo(409);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM utterance_turn WHERE session_id = ?", Integer.class, sessionId))
				.isZero();
		assertThat(sessionRow(sessionId).get("turn_count")).isEqualTo(0);
	}

	@Test
	@DisplayName("세션 조회 — 상태와 종료 사유를 돌려준다")
	void getSession() throws Exception {
		MockHttpSession user = signup("end01");
		long sessionId = start(user, 1);
		calmNext.set(true);
		submitTurn(user, sessionId);

		mockMvc.perform(get("/api/training-sessions/" + sessionId).session(user))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CALMED"))
				.andExpect(jsonPath("$.turnsToCalm").value(1))
				.andExpect(jsonPath("$.calmingCondition").doesNotExist());
		mockMvc.perform(get("/api/training-sessions/" + sessionId).session(signup("end02")))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("내 진행 중 세션 — 있으면 200, 없거나 끝났으면 204")
	void currentSession() throws Exception {
		MockHttpSession user = signup("end01");
		mockMvc.perform(get("/api/training-sessions/current").session(user))
				.andExpect(status().isNoContent());

		long sessionId = start(user, 1);
		mockMvc.perform(get("/api/training-sessions/current").session(user))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sessionId").value(sessionId))
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.calmingCondition").doesNotExist());

		mockMvc.perform(post("/api/training-sessions/" + sessionId + "/abort").session(user)).andExpect(status().isOk());
		mockMvc.perform(get("/api/training-sessions/current").session(user))
				.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("시작이 BUSY일 때 current로 내 세션인지 남의 세션인지 가린다")
	void currentDistinguishesOwnBusy() throws Exception {
		MockHttpSession owner = signup("end01");
		long sessionId = start(owner, 1);
		MockHttpSession other = signup("end02");

		// 둘 다 시작은 409
		for (MockHttpSession user : new MockHttpSession[]{owner, other}) {
			mockMvc.perform(post("/api/training-sessions").session(user)
							.contentType(MediaType.APPLICATION_JSON).content("{\"cardId\":1,\"intensity\":1}"))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("TRAINING_SESSION_BUSY"));
		}
		// 주인은 200(내 세션), 다른 사람은 204(남이 훈련 중)
		mockMvc.perform(get("/api/training-sessions/current").session(owner))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sessionId").value(sessionId));
		mockMvc.perform(get("/api/training-sessions/current").session(other))
				.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("내 진행 중 세션 조회도 로그인이 필요하다")
	void currentRequiresLogin() throws Exception {
		mockMvc.perform(get("/api/training-sessions/current"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	// ── 도우미 ───────────────────────────────────────────

	private MockHttpSession signup(String loginId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
						.content("{\"loginId\":\"%s\",\"password\":\"password1234\",\"displayName\":\"테스터\"}".formatted(loginId)))
				.andExpect(status().isCreated()).andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private long start(MockHttpSession user, long cardId) throws Exception {
		String body = mockMvc.perform(post("/api/training-sessions").session(user)
						.contentType(MediaType.APPLICATION_JSON).content("{\"cardId\":%d,\"intensity\":1}".formatted(cardId)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return Long.parseLong(body.replaceAll(".*\"sessionId\":(\\d+).*", "$1"));
	}

	private ResultActions submitTurn(MockHttpSession user, long sessionId) throws Exception {
		return mockMvc.perform(post("/api/training-sessions/" + sessionId + "/turns").session(user)
				.contentType("audio/webm").content(new byte[]{1, 2, 3}));
	}

	private Map<String, Object> sessionRow(long sessionId) {
		return jdbcTemplate.queryForMap(
				"SELECT status, turns_to_calm, active_slot, ended_at, turn_count FROM training_session WHERE id = ?", sessionId);
	}
}
