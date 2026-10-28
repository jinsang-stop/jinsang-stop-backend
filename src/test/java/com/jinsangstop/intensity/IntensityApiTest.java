package com.jinsangstop.intensity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 강도 고지 정보 API를 검증한다 — 기본값 1, 1~3단계, 2단계 이상만 고지 확인 필요.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IntensityApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("기본 강도는 1이고 2단계 이상만 고지 확인이 필요하다")
	void intensityGuide() throws Exception {
		mockMvc.perform(get("/api/intensities"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.defaultLevel").value(1))
				.andExpect(jsonPath("$.commonNotice").isNotEmpty())
				.andExpect(jsonPath("$.levels.length()").value(3))
				.andExpect(jsonPath("$.levels[0].level").value(1))
				.andExpect(jsonPath("$.levels[0].requiresConsent").value(false))
				.andExpect(jsonPath("$.levels[1].level").value(2))
				.andExpect(jsonPath("$.levels[1].requiresConsent").value(true))
				.andExpect(jsonPath("$.levels[2].level").value(3))
				.andExpect(jsonPath("$.levels[2].requiresConsent").value(true))
				.andExpect(jsonPath("$.levels[2].allowedBehaviors").isArray());
	}
}
