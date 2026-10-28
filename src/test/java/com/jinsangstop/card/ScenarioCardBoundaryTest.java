package com.jinsangstop.card;

import com.jinsangstop.card.dto.ScenarioCardSummaryResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 모듈 경계 테스트 — 프론트로 내려가는 카드 응답에 진정 조건과 양보 불가선이 없어야 한다(PRD v2 테스트 결정).
 * 응답 JSON의 키와 값, 그리고 DTO 정의 자체를 함께 검사한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScenarioCardBoundaryTest {

	/** 응답 키에 나오면 안 되는 이름(대소문자 무시, 부분 일치) */
	private static final List<String> FORBIDDEN_KEY_FRAGMENTS = List.of(
			"calming", "nonnegotiable", "non_negotiable", "persona", "demand");

	/** 응답 값에 나오면 안 되는 시드 문장 — 진정 조건·양보 불가선 원문 일부 */
	private static final List<String> FORBIDDEN_VALUE_FRAGMENTS = List.of(
			"구매 내역(영수증 또는 카드 결제 조회)을 확인하지 않고",
			"확인 가능한 대안을 분명하게 제시하면 진정한다",
			"무료 음료 쿠폰을 임의로 지급하지 않는다",
			"차분하고 분명하게 안내하면 진정한다");

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("카드 목록 응답 JSON에 진정 조건·양보 불가선 키와 원문이 없다")
	void listResponseHasNoHiddenFields() throws Exception {
		String body = mockMvc.perform(get("/api/cards"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].calmingCondition").doesNotExist())
				.andExpect(jsonPath("$[0].nonNegotiableLine").doesNotExist())
				.andReturn().getResponse().getContentAsString();

		assertNoHiddenContent(body);
	}

	@Test
	@DisplayName("카드 상세 응답 JSON에 진정 조건·양보 불가선 키와 원문이 없다")
	void detailResponseHasNoHiddenFields() throws Exception {
		for (long cardId : new long[]{1L, 2L}) {
			String body = mockMvc.perform(get("/api/cards/" + cardId))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.calmingCondition").doesNotExist())
					.andExpect(jsonPath("$.nonNegotiableLine").doesNotExist())
					.andReturn().getResponse().getContentAsString();

			assertNoHiddenContent(body);
		}
	}

	@Test
	@DisplayName("카드 요약 DTO에는 공개 필드 다섯 개만 정의돼 있다")
	void summaryDtoDeclaresOnlyPublicFields() {
		Set<String> components = Set.copyOf(Arrays.stream(ScenarioCardSummaryResponse.class.getRecordComponents())
				.map(c -> c.getName())
				.toList());

		assertThat(components).containsExactlyInAnyOrder(
				"cardId", "name", "situation", "complainantRoleName", "hardTurnLimit");
	}

	/** 응답 본문에 금지 키 조각과 금지 원문이 없는지 확인한다. */
	private void assertNoHiddenContent(String body) {
		String lowered = body.toLowerCase(Locale.ROOT);
		FORBIDDEN_KEY_FRAGMENTS.forEach(fragment -> assertThat(lowered).doesNotContain(fragment));
		FORBIDDEN_VALUE_FRAGMENTS.forEach(fragment -> assertThat(body).doesNotContain(fragment));
	}
}
