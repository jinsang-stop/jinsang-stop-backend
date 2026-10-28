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

	/** 409 — 다른 훈련 진행 중 */
	public static final String TRAINING_SESSION_BUSY = """
			{ "status": 409, "code": "TRAINING_SESSION_BUSY", "message": "다른 훈련이 진행 중입니다. 잠시 후 다시 시도해 주세요." }""";

	/** 503 — 음성 서비스 미준비 */
	public static final String TRAINING_ENVIRONMENT_NOT_READY = """
			{ "status": 503, "code": "TRAINING_ENVIRONMENT_NOT_READY", "message": "훈련 환경이 준비되지 않았습니다. 잠시 후 다시 시도해 주세요." }""";

	/** 404 — 세션 없음 */
	public static final String TRAINING_SESSION_NOT_FOUND = """
			{ "status": 404, "code": "TRAINING_SESSION_NOT_FOUND", "message": "훈련 세션을 찾을 수 없습니다." }""";

	/** 409 — 끝난 세션 */
	public static final String TRAINING_SESSION_NOT_IN_PROGRESS = """
			{ "status": 409, "code": "TRAINING_SESSION_NOT_IN_PROGRESS", "message": "이미 끝난 훈련 세션입니다." }""";

	/** 422 — 빈 전사 */
	public static final String EMPTY_TRANSCRIPT = """
			{ "status": 422, "code": "EMPTY_TRANSCRIPT", "message": "말소리가 들리지 않았어요. 다시 말해 주세요." }""";

	/** 422 — 오디오 해독 실패 */
	public static final String AUDIO_UNREADABLE = """
			{ "status": 422, "code": "AUDIO_UNREADABLE", "message": "녹음을 읽지 못했어요. 다시 말해 주세요." }""";

	/** 413 — 오디오 크기 초과 */
	public static final String AUDIO_TOO_LARGE = """
			{ "status": 413, "code": "AUDIO_TOO_LARGE", "message": "녹음이 너무 깁니다. 나눠서 말해 주세요." }""";

	/** 415 — 오디오가 아닌 형식 */
	public static final String UNSUPPORTED_MEDIA_TYPE = """
			{ "status": 415, "code": "UNSUPPORTED_MEDIA_TYPE", "message": "지원하지 않는 요청 형식입니다." }""";

	/** 404 — 카드 없음 */
	public static final String CARD_NOT_FOUND = """
			{ "status": 404, "code": "CARD_NOT_FOUND", "message": "시나리오 카드를 찾을 수 없습니다." }""";

	/** 500 — 서버 내부 오류 */
	public static final String INTERNAL_ERROR = """
			{ "status": 500, "code": "INTERNAL_ERROR", "message": "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요." }""";

	private ErrorExamples() {
	}
}
