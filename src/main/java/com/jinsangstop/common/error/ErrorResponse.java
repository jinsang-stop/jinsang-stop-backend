package com.jinsangstop.common.error;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 모든 API가 공통으로 쓰는 오류 응답 본문.
 * 프론트엔드는 `code`로 분기하고 `message`를 그대로 화면에 띄운다.
 */
@Schema(description = "공통 오류 응답 — 성공이 아닌 모든 응답이 이 형식이다")
public record ErrorResponse(
		@Schema(description = "HTTP 상태 코드(응답 상태와 같은 값)", example = "404", requiredMode = Schema.RequiredMode.REQUIRED)
		int status,
		@Schema(description = "오류 코드 — 프론트엔드는 이 값으로 분기한다",
				example = "CARD_NOT_FOUND",
				allowableValues = {"INVALID_REQUEST", "UNAUTHORIZED", "LOGIN_FAILED", "NOT_FOUND", "CARD_NOT_FOUND",
						"DUPLICATE_LOGIN_ID", "METHOD_NOT_ALLOWED", "UNSUPPORTED_MEDIA_TYPE", "TRAINING_SESSION_BUSY",
						"TRAINING_ENVIRONMENT_NOT_READY", "TRAINING_SESSION_NOT_FOUND", "TURN_NOT_FOUND", "TRAINING_SESSION_NOT_IN_PROGRESS",
						"EMPTY_TRANSCRIPT", "AUDIO_UNREADABLE", "AUDIO_TOO_LARGE", "SESSION_ENDED_BY_SYSTEM_ERROR", "INTERNAL_ERROR"},
				requiredMode = Schema.RequiredMode.REQUIRED)
		String code,
		@Schema(description = "사용자에게 그대로 보여 줘도 되는 메시지", example = "시나리오 카드를 찾을 수 없습니다.",
				requiredMode = Schema.RequiredMode.REQUIRED)
		String message
) {

	/** 오류 코드의 기본 메시지로 응답을 만든다. */
	public static ErrorResponse of(ErrorCode errorCode) {
		return new ErrorResponse(errorCode.status().value(), errorCode.name(), errorCode.message());
	}

	/** 오류 코드는 그대로 두고 메시지만 바꿔 만든다(입력 검증 실패 시 어떤 값이 잘못됐는지 알려줄 때). */
	public static ErrorResponse of(ErrorCode errorCode, String message) {
		return new ErrorResponse(errorCode.status().value(), errorCode.name(), message);
	}
}
