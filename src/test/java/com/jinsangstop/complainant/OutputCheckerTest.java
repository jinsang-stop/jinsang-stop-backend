package com.jinsangstop.complainant;

import com.jinsangstop.complainant.safety.AbsoluteLineList;
import com.jinsangstop.complainant.safety.OutputChecker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 출력 검사 테스트 — 절대선 금칙 표현과 비한국어 대사가 통과하지 못하는지(ADR-0009).
 * 금칙 목록은 실제 절대선 문서를 그대로 읽는다 — 문서에 표현을 더하면 이 테스트도 자동으로 그 표현을 검사한다.
 */
class OutputCheckerTest {

	private static final ClassPathResource DOCUMENT = new ClassPathResource("safety/absolute-lines.txt");
	private final AbsoluteLineList absoluteLineList = new AbsoluteLineList(DOCUMENT);
	private final OutputChecker checker = new OutputChecker(absoluteLineList);

	@Test
	@DisplayName("절대선 문서의 모든 금칙 표현이 대사 안에 있으면 걸린다")
	void everyDocumentedTermIsBlocked() throws Exception {
		List<String> terms = Arrays.stream(DOCUMENT.getContentAsString(StandardCharsets.UTF_8).split("\\R"))
				.map(String::strip)
				.filter(line -> !line.isEmpty() && !line.startsWith("#") && !line.startsWith("["))
				.toList();
		assertThat(terms).hasSize(absoluteLineList.size()).isNotEmpty();

		for (String term : terms) {
			OutputChecker.Result result = checker.check("아니 손님 제가 " + term + " 그런 게 아니라요");
			assertThat(result.passed()).as("금칙 표현 '%s'", term).isFalse();
			assertThat(result.reason()).startsWith("절대선 금칙 표현");
		}
	}

	@Test
	@DisplayName("띄어쓰기로 쪼갠 금칙 표현도 걸린다")
	void spacingBypassIsBlocked() {
		assertThat(checker.check("한 번만 더 그러면 죽 여 버 린 다").passed()).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"你好，请给我退款",                       // 중국어
			"영수증 없이 退款 해 주세요",              // 한자 혼입
			"すみません、返金してください",             // 일본어
			"I want a refund right now please",     // 영어
			"Refund please 지금 당장 refund",         // 라틴 문자 비율 초과
			"   ",                                   // 빈 대사
			"?!..."                                  // 한글 없음
	})
	@DisplayName("한국어가 아니거나 한자·가나가 섞이면 걸린다")
	void nonKoreanIsBlocked(String line) {
		assertThat(checker.check(line).passed()).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"영수증이 없으면 어떡하라는 거예요? 지금 바로 환불해 주세요.",
			"아니, 점장 불러와요. 말이 안 통하네.",
			"알겠어요, 그럼 카드 결제 내역으로 확인해 주세요.",
			"CU에서 산 거 맞다니까요!"   // 브랜드명 정도의 라틴 문자는 허용
	})
	@DisplayName("평범한 한국어 민원 대사는 통과한다")
	void normalKoreanPasses(String line) {
		assertThat(checker.check(line).passed()).isTrue();
	}

	@Test
	@DisplayName("금칙 표현이 하나도 없는 문서는 기동을 막는다")
	void emptyDocumentRejected() {
		var empty = new org.springframework.core.io.ByteArrayResource("# 주석만\n[분류]\n".getBytes(StandardCharsets.UTF_8));
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> new AbsoluteLineList(empty))
				.isInstanceOf(IllegalStateException.class);
	}
}
