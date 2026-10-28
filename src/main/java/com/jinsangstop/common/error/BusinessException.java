package com.jinsangstop.common.error;

/**
 * 도메인 규칙 위반을 알리는 예외. 던지면 {@link GlobalExceptionHandler}가 공통 오류 응답으로 바꾼다.
 */
public class BusinessException extends RuntimeException {

	/** 응답에 실릴 오류 코드 */
	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.message());
		this.errorCode = errorCode;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}
}
