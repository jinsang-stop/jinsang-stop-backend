package com.jinsangstop.report;

import com.jinsangstop.support.SessionFixtures;
import com.jinsangstop.support.TestDataCleaner;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 세션 리포트 API 테스트 — 종료 사유별 미편입 사유, 경계 하한선 미정 시 편입 보류, 숨은 필드 없음.
 * 세션은 저장 세션 픽스처로 DB에 바로 만든다.
 */
class SessionReportApiTest {

	private static final LocalDateTime ENDED = LocalDateTime.of(2026, 10, 28, 15, 0);

	abstract static class Base {
		@Autowired
		MockMvc mockMvc;
		@Autowired
		JdbcTemplate jdbcTemplate;
		SessionFixtures fixtures;
		MockHttpSession user;
		long userId;

		@BeforeEach
		void setUpBase() throws Exception {
			TestDataCleaner.clearUserData(jdbcTemplate);
			fixtures = new SessionFixtures(jdbcTemplate);
			user = signup("report01");
			userId = fixtures.userId("report01");
		}

		MockHttpSession signup(String loginId) throws Exception {
			MvcResult result = mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
							.content("{\"loginId\":\"%s\",\"password\":\"password1234\",\"displayName\":\"테스터\"}".formatted(loginId)))
					.andExpect(status().isCreated()).andReturn();
			return (MockHttpSession) result.getRequest().getSession(false);
		}

		ResultActions report(long sessionId) throws Exception {
			return mockMvc.perform(get("/api/training-sessions/" + sessionId + "/report").session(user));
		}
	}

	@Nested
	@SpringBootTest
	@AutoConfigureMockMvc
	@ActiveProfiles("test")
	@TestPropertySource(properties = "jinsangstop.growth.boundary-threshold=3.0")
	class ThresholdConfigured extends Base {

		@Test
		@DisplayName("진정 종료 + 모두 채점 + 경계 평균 ≥ 하한선이면 편입, 사유 없음")
		void included() throws Exception {
			long id = fixtures.session(userId, 1, 1, "CALMED", 3, ENDED);
			fixtures.scoredTurn(id, 1, 4, 3, 3);
			fixtures.scoredTurn(id, 2, 4, 4, 4);
			fixtures.scoredTurn(id, 3, 5, 4, 5);

			report(id).andExpect(status().isOk())
					.andExpect(jsonPath("$.cardName").value("영수증 없는 도시락 환불"))
					.andExpect(jsonPath("$.endReason").value("CALMED"))
					.andExpect(jsonPath("$.turnsToCalm").value(3))
					.andExpect(jsonPath("$.turns.length()").value(3))
					.andExpect(jsonPath("$.turns[0].userTranscript").value("직원 발화 1"))
					.andExpect(jsonPath("$.turns[0].complainantLine").value("손님 대사 1"))
					.andExpect(jsonPath("$.turns[2].score.boundarySetting.score").value(5))
					.andExpect(jsonPath("$.axisAverages.emotionalStability").value(4.33))
					.andExpect(jsonPath("$.axisAverages.boundarySetting").value(4.0))
					.andExpect(jsonPath("$.growthRecord.included").value(true))
					.andExpect(jsonPath("$.growthRecord.reasons", empty()));
		}

		@Test
		@DisplayName("하드 턴 상한 도달 — 진정 종료 아님, 소요 턴 수 없음")
		void turnLimit() throws Exception {
			long id = fixtures.session(userId, 1, 1, "TURN_LIMIT_REACHED", null, ENDED);
			fixtures.scoredTurn(id, 1, 4, 4, 4);

			report(id).andExpect(jsonPath("$.endReason").value("TURN_LIMIT_REACHED"))
					.andExpect(jsonPath("$.turnsToCalm").doesNotExist())
					.andExpect(jsonPath("$.growthRecord.included").value(false))
					.andExpect(jsonPath("$.growthRecord.reasons[*].code", contains("NOT_CALMED")))
					.andExpect(jsonPath("$.growthRecord.reasons[0].message").value("진정 종료로 끝나지 않은 훈련은 성장 기록에 들어가지 않아요."));
		}

		@Test
		@DisplayName("사용자 중단 — 진정 종료 아님")
		void aborted() throws Exception {
			long id = fixtures.session(userId, 1, 1, "ABORTED", null, ENDED);
			fixtures.scoredTurn(id, 1, 4, 4, 4);

			report(id).andExpect(jsonPath("$.endReason").value("ABORTED"))
					.andExpect(jsonPath("$.growthRecord.reasons[*].code", contains("NOT_CALMED")));
		}

		@Test
		@DisplayName("시스템 오류로 중단 — 진정 종료 아님")
		void systemError() throws Exception {
			long id = fixtures.session(userId, 1, 1, "SYSTEM_ERROR", null, ENDED);
			fixtures.scoredTurn(id, 1, 4, 4, 4);

			report(id).andExpect(jsonPath("$.endReason").value("SYSTEM_ERROR"))
					.andExpect(jsonPath("$.growthRecord.reasons[*].code", contains("NOT_CALMED")));
		}

		@Test
		@DisplayName("채점 실패 턴이 있으면 정보 없음으로 보이고 사유가 붙는다")
		void unscoredTurn() throws Exception {
			long id = fixtures.session(userId, 1, 1, "CALMED", 2, ENDED);
			fixtures.scoredTurn(id, 1, 4, 4, 4);
			fixtures.failedTurn(id, 2);

			report(id).andExpect(jsonPath("$.turns[1].score.status").value("FAILED"))
					.andExpect(jsonPath("$.turns[1].score.emotionalStability").doesNotExist())
					.andExpect(jsonPath("$.axisAverages.boundarySetting").value(4.0))
					.andExpect(jsonPath("$.growthRecord.included").value(false))
					.andExpect(jsonPath("$.growthRecord.reasons[*].code", contains("UNSCORED_TURNS")));
		}

		@Test
		@DisplayName("요구를 다 들어줘 2턴 만에 진정했어도 경계 설정이 하한선 미만이면 미편입")
		void boundaryBelowThreshold() throws Exception {
			long id = fixtures.session(userId, 1, 1, "CALMED", 2, ENDED);
			fixtures.scoredTurn(id, 1, 5, 5, 2);
			fixtures.scoredTurn(id, 2, 5, 5, 1);

			report(id).andExpect(jsonPath("$.growthRecord.included").value(false))
					.andExpect(jsonPath("$.growthRecord.reasons[*].code", contains("BOUNDARY_BELOW_THRESHOLD")));
		}

		@Test
		@DisplayName("사유는 여러 개가 함께 붙을 수 있다")
		void multipleReasons() throws Exception {
			long id = fixtures.session(userId, 1, 1, "TURN_LIMIT_REACHED", null, ENDED);
			fixtures.scoredTurn(id, 1, 3, 3, 1);
			fixtures.failedTurn(id, 2);

			report(id).andExpect(jsonPath("$.growthRecord.reasons[*].code",
					contains("NOT_CALMED", "UNSCORED_TURNS", "BOUNDARY_BELOW_THRESHOLD")));
		}

		@Test
		@DisplayName("리포트 JSON에 진정 조건·양보 불가선·합산 점수·올바른 조치가 없다")
		void noHiddenFields() throws Exception {
			long id = fixtures.session(userId, 1, 1, "CALMED", 1, ENDED);
			fixtures.scoredTurn(id, 1, 4, 4, 4);

			String body = report(id).andReturn().getResponse().getContentAsString();
			String lowered = body.toLowerCase(Locale.ROOT);
			assertThat(lowered).doesNotContain("calming", "nonnegotiable", "non_negotiable", "persona", "demand",
					"total", "overall", "올바른", "정답", "매뉴얼");
			assertThat(body).doesNotContain("구매 내역(영수증 또는 카드 결제 조회)을 확인하지 않고",
					"확인 가능한 대안을 분명하게 제시하면 진정한다");
		}

		@Test
		@DisplayName("진행 중인 세션은 409, 남의 세션은 404")
		void inProgressAndOthers() throws Exception {
			long inProgress = fixtures.session(userId, 1, 1, "IN_PROGRESS", null, null);
			report(inProgress).andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("TRAINING_SESSION_IN_PROGRESS"));

			signup("report02");
			long others = fixtures.session(fixtures.userId("report02"), 1, 1, "CALMED", 1, ENDED);
			report(others).andExpect(status().isNotFound())
					.andExpect(jsonPath("$.code").value("TRAINING_SESSION_NOT_FOUND"));
		}
	}

	@Nested
	@SpringBootTest
	@AutoConfigureMockMvc
	@ActiveProfiles("test")
	class ThresholdUnset extends Base {

		@Test
		@DisplayName("경계 하한선이 정해지지 않으면 완벽한 진정 종료 세션도 편입 보류")
		void allSessionsOnHold() throws Exception {
			long calmed = fixtures.session(userId, 1, 1, "CALMED", 2, ENDED);
			fixtures.scoredTurn(calmed, 1, 5, 5, 5);
			fixtures.scoredTurn(calmed, 2, 5, 5, 5);
			long aborted = fixtures.session(userId, 1, 1, "ABORTED", null, ENDED);
			fixtures.scoredTurn(aborted, 1, 5, 5, 5);

			report(calmed).andExpect(jsonPath("$.growthRecord.included").value(false))
					.andExpect(jsonPath("$.growthRecord.reasons[*].code", contains("BOUNDARY_THRESHOLD_UNSET")))
					.andExpect(jsonPath("$.growthRecord.reasons[0].message").value("경계 설정 기준이 아직 정해지지 않아 성장 기록 편입을 보류했어요."));
			report(aborted).andExpect(jsonPath("$.growthRecord.reasons[*].code", contains("NOT_CALMED", "BOUNDARY_THRESHOLD_UNSET")));
		}
	}
}
