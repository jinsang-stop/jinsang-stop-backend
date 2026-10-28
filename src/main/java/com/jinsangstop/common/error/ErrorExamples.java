package com.jinsangstop.common.error;

/**
 * Swagger 문서에 싣는 오류 응답 예시 JSON 모음. 컨트롤러의 @ExampleObject에서 참조한다.
 * 값은 {@link ErrorCode}의 실제 메시지와 같게 유지한다.
 */
public final class ErrorExamples {

	/** 400 — 요청 형식 오류 */
	public static final String INVALID_REQUEST = """
			{ "status": 400, "code": "INVALID_REQUEST", "message": "요청 형식이 올바르지 않습니다." }""";

	/** 400 — 입력 검증 실패(첫 번째 위반 항목의 메시지) */
	public static final String INVALID_SIGNUP = """
			{ "status": 400, "code": "INVALID_REQUEST", "message": "아이디는 영문 소문자·숫자·밑줄(_) 4~20자입니다." }""";

	/** 401 — 로그인 필요 */
	public static final String UNAUTHORIZED = """
			{ "status": 401, "code": "UNAUTHORIZED", "message": "로그인이 필요합니다." }""";

	/** 401 — 아이디·비밀번호 불일치 */
	public static final String LOGIN_FAILED = """
			{ "status": 401, "code": "LOGIN_FAILED", "message": "아이디 또는 비밀번호가 올바르지 않습니다." }""";

	/** 409 — 아이디 중복 */
	public static final String DUPLICATE_LOGIN_ID = """
			{ "status": 409, "code": "DUPLICATE_LOGIN_ID", "message": "이미 사용 중인 아이디입니다." }""";

	/** 404 — 카드 없음 */
	public static final String CARD_NOT_FOUND = """
			{ "status": 404, "code": "CARD_NOT_FOUND", "message": "시나리오 카드를 찾을 수 없습니다." }""";

	/** 500 — 서버 내부 오류 */
	public static final String INTERNAL_ERROR = """
			{ "status": 500, "code": "INTERNAL_ERROR", "message": "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요." }""";

	private ErrorExamples() {
	}
}
