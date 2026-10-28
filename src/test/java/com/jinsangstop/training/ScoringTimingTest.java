package com.jinsangstop.training;

import com.jinsangstop.inference.InferenceClient;
import com.jinsangstop.scoring.AttitudeScorer;
import com.jinsangstop.support.TestDataCleaner;
import com.jinsangstop.voice.VoiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 채점 호출 시점 검증 — 채점이 턴 응답보다 늦으면 PENDING으로 응답하고 끝나는 대로 저장하는지,
 * 순차 모드에서 채점이 연기 응답 뒤에 시작하는지.
 */
class ScoringTimingTest {

	private static final String ACT = "{\"대사\": \"영수증이 없으면 어떡하라는 거예요?\", \"진정함\": false}";
	private static final String SCORE = """
			{"정서안정": {"점수": 3, "근거": "보통이다."}, "공감인정": {"점수": 3, "근거": "보통이다."}, "경계설정": {"점수": 3, "근거": "보통이다."}}""";

	/** 두 모드가 공유하는 준비 코드 */
	abstract static class Base {
		@Autowired
		MockMvc mockMvc;
		@Autowired
		JdbcTemplate jdbcTemplate;
		@MockitoBean
		VoiceClient voiceClient;
		@MockitoBean
		InferenceClient inferenceClient;

		@BeforeEach
		void setUpBase() {
			TestDataCleaner.clearUserData(jdbcTemplate);
			when(voiceClient.isReady()).thenReturn(true);
			when(inferenceClient.isReady()).thenReturn(true);
			when(voiceClient.transcribe(any(), anyString())).thenReturn("죄송합니다 손님");
			when(voiceClient.synthesize(anyString())).thenReturn("RIFF".getBytes());
		}

		MockHttpSession signupAndStart() throws Exception {
			MvcResult signup = mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
							.content("{\"loginId\":\"timing01\",\"password\":\"password1234\",\"displayName\":\"테스터\"}"))
					.andExpect(status().isCreated()).andReturn();
			MockHttpSession user = (MockHttpSession) signup.getRequest().getSession(false);
			mockMvc.perform(post("/api/training-sessions").session(user)
							.contentType(MediaType.APPLICATION_JSON).content("{\"cardId\":1,\"intensity\":1}"))
					.andExpect(status().isCreated());
			return user;
		}

		long sessionId() {
			return jdbcTemplate.queryForObject("SELECT MAX(id) FROM training_session", Long.class);
		}
	}

	@Nested
	@SpringBootTest
	@AutoConfigureMockMvc
	@ActiveProfiles("test")
	@TestPropertySource(properties = {"jinsangstop.scoring.mode=PARALLEL", "jinsangstop.scoring.response-wait=200ms"})
	class LateScore extends Base {

		@Test
		@DisplayName("채점이 늦으면 턴 응답은 PENDING이고, 채점이 끝나면 점수 조회 API에 반영된다")
		void pendingThenScored() throws Exception {
			CountDownLatch release = new CountDownLatch(1);
			when(inferenceClient.complete(any())).thenAnswer(invocation -> {
				InferenceClient.ChatRequest request = invocation.getArgument(0);
				if (request.slot() == AttitudeScorer.SCORING_SLOT) {
					release.await(10, TimeUnit.SECONDS);
					return SCORE;
				}
				return ACT;
			});
			MockHttpSession user = signupAndStart();
			long sessionId = sessionId();

			mockMvc.perform(post("/api/training-sessions/" + sessionId + "/turns").session(user)
							.contentType("audio/webm").content(new byte[]{1, 2, 3}))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.score.status").value("PENDING"))
					.andExpect(jsonPath("$.score.emotionalStability").doesNotExist());

			release.countDown();
			String status = "PENDING";
			for (int i = 0; i < 50 && status.equals("PENDING"); i++) {
				Thread.sleep(100);
				status = jdbcTemplate.queryForObject(
						"SELECT score_status FROM utterance_turn WHERE session_id = ?", String.class, sessionId);
			}
			assertThat(status).isEqualTo("SCORED");
			mockMvc.perform(get("/api/training-sessions/" + sessionId + "/turns/1/score").session(user))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.status").value("SCORED"))
					.andExpect(jsonPath("$.boundarySetting.score").value(3));
		}
	}

	@Nested
	@SpringBootTest
	@AutoConfigureMockMvc
	@ActiveProfiles("test")
	@TestPropertySource(properties = {"jinsangstop.scoring.mode=SEQUENTIAL"})
	class Sequential extends Base {

		@Test
		@DisplayName("순차 모드에서는 연기 응답이 끝난 뒤에 채점을 시작한다")
		void scoringStartsAfterActing() throws Exception {
			List<String> events = new CopyOnWriteArrayList<>();
			when(inferenceClient.complete(any())).thenAnswer(invocation -> {
				InferenceClient.ChatRequest request = invocation.getArgument(0);
				if (request.slot() == AttitudeScorer.SCORING_SLOT) {
					events.add("score-start");
					return SCORE;
				}
				events.add("act-start");
				Thread.sleep(300);
				events.add("act-end");
				return ACT;
			});
			MockHttpSession user = signupAndStart();

			mockMvc.perform(post("/api/training-sessions/" + sessionId() + "/turns").session(user)
							.contentType("audio/webm").content(new byte[]{1, 2, 3}))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.score.status").value("SCORED"));

			assertThat(events).containsExactly("act-start", "act-end", "score-start");
		}
	}
}
