package com.jinsangstop.training.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 확정된 발화 턴. 민원인 응답(#4)과 점수(#5)는 이후 슬라이스에서 이 응답에 붙는다.
 */
@Schema(description = "확정된 발화 턴")
public record UtteranceTurnResponse(
		@Schema(description = "세션 식별자", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
		Long sessionId,
		@Schema(description = "턴 번호(1부터). 빈 전사는 번호를 소모하지 않는다", example = "1",
				requiredMode = Schema.RequiredMode.REQUIRED)
		int turnNumber,
		@Schema(description = "사용자 발화 전사 — 대화 화면에 내 말로 표시", example = "죄송합니다 손님",
				requiredMode = Schema.RequiredMode.REQUIRED)
		String userTranscript
) {
}
