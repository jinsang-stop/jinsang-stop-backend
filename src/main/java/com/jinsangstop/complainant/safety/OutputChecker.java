package com.jinsangstop.complainant.safety;

import org.springframework.stereotype.Component;

/**
 * 출력 검사 — 민원인 대사를 음성으로 합성하기 전에 거르는 검사(ADR-0009).
 * 1) 절대선 금칙 표현이 없을 것 2) 한국어일 것. 사용자의 점수를 매기는 것과는 무관하다.
 */
@Component
public class OutputChecker {

	/** 검사 결과 */
	public record Result(boolean passed, String reason) {

		static Result pass() {
			return new Result(true, null);
		}

		static Result fail(String reason) {
			return new Result(false, reason);
		}
	}

	/** 한글 대비 허용하는 라틴 문자 비율 — 브랜드명 정도만 허용한다 */
	private static final double MAX_LATIN_RATIO = 0.3;

	private final AbsoluteLineList absoluteLineList;

	public OutputChecker(AbsoluteLineList absoluteLineList) {
		this.absoluteLineList = absoluteLineList;
	}

	/** 대사를 검사한다. */
	public Result check(String line) {
		if (line == null || line.isBlank()) {
			return Result.fail("빈 대사");
		}
		var violation = absoluteLineList.findViolation(line);
		if (violation.isPresent()) {
			return Result.fail("절대선 금칙 표현(" + violation.get().category() + ")");
		}
		return checkKorean(line);
	}

	/**
	 * 한국어 검사 — 한자·가나가 하나라도 있으면 실패(Qwen 계열의 한자·중국어 혼입),
	 * 한글이 없거나 라틴 문자가 한글의 30%를 넘으면 실패.
	 */
	private Result checkKorean(String line) {
		int hangul = 0;
		int latin = 0;
		for (int i = 0; i < line.length(); ) {
			int codePoint = line.codePointAt(i);
			Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
			switch (script) {
				case HAN -> {
					return Result.fail("한자 포함");
				}
				case HIRAGANA, KATAKANA -> {
					return Result.fail("일본어 문자 포함");
				}
				case HANGUL -> hangul++;
				case LATIN -> latin++;
				default -> {
				}
			}
			i += Character.charCount(codePoint);
		}
		if (hangul == 0) {
			return Result.fail("한글 없음");
		}
		if (latin > hangul * MAX_LATIN_RATIO) {
			return Result.fail("라틴 문자 비율 초과");
		}
		return Result.pass();
	}
}
