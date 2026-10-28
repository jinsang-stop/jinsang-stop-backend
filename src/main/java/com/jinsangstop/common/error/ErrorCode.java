package com.jinsangstop.common.error;

import org.springframework.http.HttpStatus;

/**
 * 프론트엔드에 내려가는 오류 코드 목록.
 * 상태 코드 · 오류 코드 · 사용자 표시용 메시지를 한곳에서 관리하고, 이후 슬라이스도 여기에 코드를 추가한다.
 */
public enum ErrorCode {

	/** 요청 형식이 잘못됨(경로 변수 타입 불일치, 검증 실패 등) */
	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식이 올바르지 않습니다."),
	/** 로그인하지 않은 상태로 보호된 API를 호출함 */
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	/** 아이디 또는 비밀번호 불일치 — 어느 쪽이 틀렸는지는 알려주지 않는다 */
	LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
	/** 존재하지 않는 경로 */
	NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 주소를 찾을 수 없습니다."),
	/** 회원가입 시 이미 쓰이는 아이디 */
	DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 사용 중인 아이디입니다."),
	/** 지원하지 않는 HTTP 메서드 */
	METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
	/** 요청 본문 형식(Content-Type)이 맞지 않음 — 예: 턴 제출에 오디오가 아닌 형식 */
	UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),

	/** 다른 훈련 세션이 진행 중 — 시연 장비 한 대가 한 번에 한 세션만 돌린다 */
	TRAINING_SESSION_BUSY(HttpStatus.CONFLICT, "다른 훈련이 진행 중입니다. 잠시 후 다시 시도해 주세요."),
	/** 음성 서비스가 응답하지 않거나 준비되지 않음 */
	TRAINING_ENVIRONMENT_NOT_READY(HttpStatus.SERVICE_UNAVAILABLE, "훈련 환경이 준비되지 않았습니다. 잠시 후 다시 시도해 주세요."),
	/** 없는 세션이거나 다른 사용자의 세션 */
	TRAINING_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "훈련 세션을 찾을 수 없습니다."),
	/** 세션에 그 번호의 턴이 없음 */
	TURN_NOT_FOUND(HttpStatus.NOT_FOUND, "발화 턴을 찾을 수 없습니다."),
	/** 같은 세션의 이전 턴을 아직 처리 중 — 두 번째 제출은 버린다 */
	TURN_ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, "이전 발화를 처리하고 있어요. 잠시만 기다려 주세요."),
	/** 이미 끝난 세션에 턴을 제출함 */
	TRAINING_SESSION_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "이미 끝난 훈련 세션입니다."),
	/** 전사 결과가 비었음 — 발화 턴을 소모하지 않는다 */
	EMPTY_TRANSCRIPT(HttpStatus.UNPROCESSABLE_CONTENT, "말소리가 들리지 않았어요. 다시 말해 주세요."),
	/** 보낸 오디오를 읽지 못함 — 다시 녹음하면 풀리는 문제 */
	AUDIO_UNREADABLE(HttpStatus.UNPROCESSABLE_CONTENT, "녹음을 읽지 못했어요. 다시 말해 주세요."),
	/** 민원인 대사가 재시도까지 출력 검사를 통과하지 못해 세션을 시스템 오류로 끝냄 */
	SESSION_ENDED_BY_SYSTEM_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 문제로 훈련을 이어갈 수 없어 종료했습니다. 새 훈련을 시작해 주세요."),
	/** 오디오가 허용 크기를 넘음 */
	AUDIO_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "녹음이 너무 깁니다. 나눠서 말해 주세요."),
	/** 해당 식별자의 공식 카드가 없음 */
	CARD_NOT_FOUND(HttpStatus.NOT_FOUND, "시나리오 카드를 찾을 수 없습니다."),
	/** 처리하지 못한 서버 내부 오류 */
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");

	private final HttpStatus status;
	private final String message;

	ErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	/** 응답에 쓸 HTTP 상태 */
	public HttpStatus status() {
		return status;
	}

	/** 사용자에게 보여줄 기본 메시지 */
	public String message() {
		return message;
	}
}
