package com.jinsangstop.training.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 확정된 발화 턴 — 사용자 전사 + 민원인 응답(대사·합성 음성·진정 여부).
 * 점수(#5)는 이후 슬라이스에서 붙는다.
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
		String userTranscript,
		@Schema(description = "민원인 대사 — 화면에 역할명(complainantRoleName)과 함께 표시. 출력 검사를 통과한 것만 온다",
				example = "죄송하면 다예요? 지금 바로 환불해 달라고요.", requiredMode = Schema.RequiredMode.REQUIRED)
		String complainantLine,
		@Schema(description = "민원인이 이 턴에서 진정했는지", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
		boolean calmed,
		@Schema(description = "민원인 대사 합성 음성(Base64). 디코딩해 바로 재생한다 — 서버에 보관하지 않으므로 다시 받을 수 없다",
				example = "UklGRiQAAABXQVZFZm10IBAAAAABAAEARKwAAIhYAQACABAAZGF0YQAAAAA=",
				requiredMode = Schema.RequiredMode.REQUIRED)
		String complainantAudio,
		@Schema(description = "합성 음성 형식", example = "audio/wav", requiredMode = Schema.RequiredMode.REQUIRED)
		String complainantAudioContentType
) {
}
