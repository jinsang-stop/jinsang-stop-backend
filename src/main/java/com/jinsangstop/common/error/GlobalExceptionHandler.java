package com.jinsangstop.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 컨트롤러에서 새어 나온 예외를 공통 오류 응답({@link ErrorResponse})으로 바꾼다.
 * 스택 트레이스나 내부 메시지는 응답에 싣지 않는다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** 도메인 예외 — 예외에 담긴 오류 코드를 그대로 내려준다. */
	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
		return toResponse(e.getErrorCode());
	}

	/** 경로 변수·쿼리 파라미터 타입이 맞지 않을 때(예: 카드 식별자에 문자) */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
		return toResponse(ErrorCode.INVALID_REQUEST);
	}

	/** 요청 본문 검증(@Valid) 실패 — 첫 번째 위반 항목의 메시지를 사용자에게 보여준다. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
		String message = e.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(FieldError::getDefaultMessage)
				.orElse(ErrorCode.INVALID_REQUEST.message());
		return ResponseEntity.status(ErrorCode.INVALID_REQUEST.status())
				.body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, message));
	}

	/** 요청 본문이 비었거나 JSON 형식이 깨짐 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
		return toResponse(ErrorCode.INVALID_REQUEST);
	}

	/** 지원하지 않는 HTTP 메서드(예: GET만 있는 주소에 POST) */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
		return toResponse(ErrorCode.METHOD_NOT_ALLOWED);
	}

	/** 요청 본문의 Content-Type을 받지 않는 API(예: 턴 제출에 JSON) */
	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
		return toResponse(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
	}

	/** 매핑되지 않은 경로 */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
		return toResponse(ErrorCode.NOT_FOUND);
	}

	/** 그 밖의 모든 예외 — 로그만 남기고 사용자에게는 일반 메시지를 준다. */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
		log.error("처리하지 못한 예외", e);
		return toResponse(ErrorCode.INTERNAL_ERROR);
	}

	/** 오류 코드를 HTTP 응답으로 감싼다. */
	private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode) {
		return ResponseEntity.status(errorCode.status()).body(ErrorResponse.of(errorCode));
	}
}
