package com.jinsangstop.common.error;

/**
 * Swagger 문서에 싣는 오류 응답 예시 JSON 모음. 컨트롤러의 @ExampleObject에서 참조한다.
 * 값은 {@link ErrorCode}의 실제 메시지와 같게 유지한다.
 */
public final class ErrorExamples {

	/** 400 — 요청 형식 오류 */
	public static final String INVALID_REQUEST = """
			{ "status": 400, "code": "INVALID_REQUEST", "message": "요청 형식이 올바르지 않습니다." }""";

	/** 404 — 카드 없음 */
	public static final String CARD_NOT_FOUND = """
			{ "status": 404, "code": "CARD_NOT_FOUND", "message": "시나리오 카드를 찾을 수 없습니다." }""";

	/** 500 — 서버 내부 오류 */
	public static final String INTERNAL_ERROR = """
			{ "status": 500, "code": "INTERNAL_ERROR", "message": "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요." }""";

	private ErrorExamples() {
	}
}
