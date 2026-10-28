package com.jinsangstop.growth;

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

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 성장 기록 조회 테스트 — 저장 세션 픽스처로 편입 조건을 확인한다.
 */
class GrowthRecordApiTest {

	private static final LocalDateTime DAY1 = LocalDateTime.of(2026, 10, 21, 15, 0);
	private static final LocalDateTime DAY2 = LocalDateTime.of(2026, 10, 24, 15, 0);
	private static final LocalDateTime DAY3 = LocalDateTime.of(2026, 10, 28, 15, 0);

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
			user = signup("growth01");
			userId = fixtures.userId("growth01");
		}

		MockHttpSession signup(String loginId) throws Exception {
			MvcResult result = mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
							.content("{\"loginId\":\"%s\",\"password\":\"password1234\",\"displayName\":\"테스터\"}".formatted(loginId)))
					.andExpect(status().isCreated()).andReturn();
			return (MockHttpSession) result.getRequest().getSession(false);
		}

		ResultActions growth(long cardId, int intensity) throws Exception {
			return mockMvc.perform(get("/api/growth-records").session(user)
					.param("cardId", String.valueOf(cardId)).param("intensity", String.valueOf(intensity)));
		}

		/** 편입 조건을 만족하는 진정 종료 세션 */
		long goodSession(long owner, int intensity, int turnsToCalm, LocalDateTime endedAt) {
			long id = fixtures.session(owner, 1, intensity, "CALMED", turnsToCalm, endedAt);
			for (int n = 1; n <= turnsToCalm; n++) {
				fixtures.scoredTurn(id, n, 4, 4, 4);
			}
			return id;
		}
	}

	@Nested
	@SpringBootTest
	@AutoConfigureMockMvc
	@ActiveProfiles("test")
	@TestPropertySource(properties = "jinsangstop.growth.boundary-threshold=3.0")
	class ThresholdConfigured extends Base {

		@Test
		@DisplayName("편입 조건을 만족한 세션만 종료 시각 순으로 나온다")
		void includedSessionsInOrder() throws Exception {
			long later = goodSession(userId, 1, 4, DAY3);
			long earlier = goodSession(userId, 1, 7, DAY1);

			growth(1, 1).andExpect(status().isOk())
					.andExpect(jsonPath("$.cardName").value("영수증 없는 도시락 환불"))
					.andExpect(jsonPath("$.intensity").value(1))
					.andExpect(jsonPath("$.boundaryThresholdConfigured").value(true))
					.andExpect(jsonPath("$.points[*].sessionId", contains((int) earlier, (int) later)))
					.andExpect(jsonPath("$.points[0].turnsToCalm").value(7))
					.andExpect(jsonPath("$.points[1].turnsToCalm").value(4))
					.andExpect(jsonPath("$.points[1].boundarySetting").value(4.0))
					.andExpect(jsonPath("$.points[1].total").doesNotExist());
		}

		@Test
		@DisplayName("요구를 전부 수용해 2턴 만에 진정 종료됐지만 경계 설정이 하한선 미만인 세션은 없다")
		void concedingFastIsExcluded() throws Exception {
			long good = goodSession(userId, 1, 5, DAY1);
			long conceded = fixtures.session(userId, 1, 1, "CALMED", 2, DAY2);
			fixtures.scoredTurn(conceded, 1, 5, 5, 2);
			fixtures.scoredTurn(conceded, 2, 5, 5, 1);

			growth(1, 1).andExpect(jsonPath("$.points[*].sessionId", contains((int) good)));
		}

		@Test
		@DisplayName("강도가 다른 세션은 같은 시계열에 섞이지 않는다")
		void intensitiesNotMixed() throws Exception {
			long level1 = goodSession(userId, 1, 5, DAY1);
			long level2 = goodSession(userId, 2, 3, DAY2);

			growth(1, 1).andExpect(jsonPath("$.points[*].sessionId", contains((int) level1)));
			growth(1, 2).andExpect(jsonPath("$.points[*].sessionId", contains((int) level2)))
					.andExpect(jsonPath("$.intensity").value(2));
		}

		@Test
		@DisplayName("다른 사용자의 세션은 결과에 없다")
		void otherUsersExcluded() throws Exception {
			long mine = goodSession(userId, 1, 5, DAY1);
			signup("growth02");
			goodSession(fixtures.userId("growth02"), 1, 2, DAY2);

			growth(1, 1).andExpect(jsonPath("$.points[*].sessionId", contains((int) mine)));
		}

		@Test
		@DisplayName("진정 종료가 아니거나 채점되지 않은 턴이 있는 세션은 없다")
		void notCalmedOrUnscoredExcluded() throws Exception {
			long good = goodSession(userId, 1, 5, DAY1);
			long limit = fixtures.session(userId, 1, 1, "TURN_LIMIT_REACHED", null, DAY2);
			fixtures.scoredTurn(limit, 1, 4, 4, 4);
			long aborted = fixtures.session(userId, 1, 1, "ABORTED", null, DAY2);
			fixtures.scoredTurn(aborted, 1, 4, 4, 4);
			long unscored = fixtures.session(userId, 1, 1, "CALMED", 2, DAY3);
			fixtures.scoredTurn(unscored, 1, 4, 4, 4);
			fixtures.failedTurn(unscored, 2);

			growth(1, 1).andExpect(jsonPath("$.points[*].sessionId", contains((int) good)));
		}

		@Test
		@DisplayName("점이 없으면 빈 배열")
		void noPoints() throws Exception {
			growth(2, 3).andExpect(status().isOk()).andExpect(jsonPath("$.points", empty()));
		}

		@Test
		@DisplayName("강도 범위 밖·누락은 400, 없는 카드는 404, 로그인 없으면 401")
		void invalidRequests() throws Exception {
			growth(1, 4).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
			mockMvc.perform(get("/api/growth-records").session(user).param("cardId", "1"))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
			growth(9999, 1).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CARD_NOT_FOUND"));
			mockMvc.perform(get("/api/growth-records").param("cardId", "1").param("intensity", "1"))
					.andExpect(status().isUnauthorized());
		}
	}

	@Nested
	@SpringBootTest
	@AutoConfigureMockMvc
	@ActiveProfiles("test")
	class ThresholdUnset extends Base {

		@Test
		@DisplayName("경계 하한선이 미정이면 점이 없고 그 사실을 알려준다")
		void onHold() throws Exception {
			goodSession(userId, 1, 5, DAY1);

			growth(1, 1).andExpect(jsonPath("$.boundaryThresholdConfigured").value(false))
					.andExpect(jsonPath("$.points", empty()));
		}
	}
}
