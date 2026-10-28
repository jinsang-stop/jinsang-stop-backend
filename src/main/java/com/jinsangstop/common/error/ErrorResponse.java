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
				allowableValues = {"INVALID_REQUEST", "NOT_FOUND", "CARD_NOT_FOUND", "INTERNAL_ERROR"},
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
}
