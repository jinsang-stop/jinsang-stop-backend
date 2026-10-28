package com.jinsangstop.inference;

/**
 * 추론 서비스 호출 실패(연결 거부·시간 초과·오류 응답·빈 응답).
 */
public class InferenceServiceException extends RuntimeException {

	public InferenceServiceException(String message) {
		super(message);
	}

	public InferenceServiceException(String message, Throwable cause) {
		super(message, cause);
	}
}
