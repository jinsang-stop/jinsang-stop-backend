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
