package com.jinsangstop.docs;

import com.jinsangstop.card.controller.ScenarioCardController;
import com.jinsangstop.common.error.ErrorExamples;
import com.jinsangstop.intensity.controller.IntensityController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Swagger 문서의 예시가 실제 응답과 같은지 검사한다.
 * 프론트엔드는 문서 예시를 보고 타입을 만들기 때문에, 예시가 틀리면 연동이 어긋난다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiExampleTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("카드 목록 예시는 실제 응답과 같다")
	void cardListExampleMatches() throws Exception {
		mockMvc.perform(get("/api/cards"))
				.andExpect(status().isOk())
				.andExpect(content().json(ScenarioCardController.LIST_EXAMPLE, JsonCompareMode.STRICT));
	}

	@Test
	@DisplayName("카드 상세 예시는 실제 응답과 같다")
	void cardDetailExampleMatches() throws Exception {
		mockMvc.perform(get("/api/cards/1"))
				.andExpect(status().isOk())
				.andExpect(content().json(ScenarioCardController.DETAIL_EXAMPLE, JsonCompareMode.STRICT));
	}

	@Test
	@DisplayName("강도 예시는 실제 응답과 같다")
	void intensityExampleMatches() throws Exception {
		mockMvc.perform(get("/api/intensities"))
				.andExpect(status().isOk())
				.andExpect(content().json(IntensityController.GUIDE_EXAMPLE, JsonCompareMode.STRICT));
	}

	@Test
	@DisplayName("오류 예시는 실제 오류 응답과 같다")
	void errorExamplesMatch() throws Exception {
		mockMvc.perform(get("/api/cards/9999"))
				.andExpect(status().isNotFound())
				.andExpect(content().json(ErrorExamples.CARD_NOT_FOUND, JsonCompareMode.STRICT));
		mockMvc.perform(get("/api/cards/abc"))
				.andExpect(status().isBadRequest())
				.andExpect(content().json(ErrorExamples.INVALID_REQUEST, JsonCompareMode.STRICT));
	}

	@Test
	@DisplayName("API 문서에 연동 안내·태그·예시가 실린다")
	void apiDocsContainGuide() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.description").value(containsString("인증 — 세션 쿠키")))
				.andExpect(jsonPath("$.tags[0].name").value("1. 인증"))
				.andExpect(jsonPath("$.tags[1].name").value("2. 강도"))
				.andExpect(jsonPath("$.tags[2].name").value("3. 시나리오 카드"))
				.andExpect(jsonPath("$.tags[3].name").value("4. 훈련 세션"))
				.andExpect(jsonPath("$.paths['/api/training-sessions/{sessionId}/turns'].post.requestBody.content['audio/webm']").exists())
				.andExpect(jsonPath("$.paths['/api/training-sessions/{sessionId}/turns'].post.responses['422'].content['application/json'].examples.EMPTY_TRANSCRIPT").exists())
				.andExpect(jsonPath("$.components.securitySchemes.sessionCookie.in").value("cookie"))
				.andExpect(jsonPath("$.paths['/api/auth/me'].get.security[0].sessionCookie").exists())
				.andExpect(jsonPath("$.paths['/api/cards/{cardId}'].get.responses['404'].content['application/json'].examples.CARD_NOT_FOUND").exists())
				.andExpect(jsonPath("$.paths['/api/intensities'].get.responses['200'].content['application/json'].examples").exists());
	}
}
