package com.jinsangstop.card;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 공식 카드 목록·상세 API와 공통 오류 응답 형식을 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScenarioCardApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("공식 카드 목록은 시드된 카드를 요약 필드로 돌려준다")
	void listOfficialCards() throws Exception {
		mockMvc.perform(get("/api/cards"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)))
				.andExpect(jsonPath("$[0].cardId").value(1))
				.andExpect(jsonPath("$[0].name").value("영수증 없는 도시락 환불"))
				.andExpect(jsonPath("$[0].situation").isNotEmpty())
				.andExpect(jsonPath("$[0].complainantRoleName").value("손님"))
				.andExpect(jsonPath("$[0].hardTurnLimit").value(12));
	}

	@Test
	@DisplayName("공식 카드 상세는 같은 요약 형식으로 돌려준다")
	void getOfficialCard() throws Exception {
		mockMvc.perform(get("/api/cards/2"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cardId").value(2))
				.andExpect(jsonPath("$.complainantRoleName").value("손님"))
				.andExpect(jsonPath("$.hardTurnLimit").value(10));
	}

	@Test
	@DisplayName("없는 카드는 404 CARD_NOT_FOUND 공통 오류 응답")
	void cardNotFound() throws Exception {
		mockMvc.perform(get("/api/cards/9999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.code").value("CARD_NOT_FOUND"))
				.andExpect(jsonPath("$.message").value("시나리오 카드를 찾을 수 없습니다."));
	}

	@Test
	@DisplayName("숫자가 아닌 카드 식별자는 400 INVALID_REQUEST")
	void invalidCardId() throws Exception {
		mockMvc.perform(get("/api/cards/abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("매핑되지 않은 경로는 404 NOT_FOUND")
	void unknownPath() throws Exception {
		mockMvc.perform(get("/api/unknown"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	@DisplayName("허용된 프론트 출처의 요청에 CORS 헤더가 붙는다")
	void corsAllowedOrigin() throws Exception {
		mockMvc.perform(get("/api/cards").header("Origin", "http://localhost:5173"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
	}

	@Test
	@DisplayName("허용되지 않은 출처의 요청은 거부된다")
	void corsRejectedOrigin() throws Exception {
		mockMvc.perform(get("/api/cards").header("Origin", "http://evil.example"))
				.andExpect(status().isForbidden());
	}
}
