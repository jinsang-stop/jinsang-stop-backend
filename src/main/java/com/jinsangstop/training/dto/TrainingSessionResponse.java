package com.jinsangstop.training.dto;

import com.jinsangstop.card.domain.ScenarioCard;
import com.jinsangstop.training.domain.TrainingSession;
import com.jinsangstop.training.domain.TrainingSessionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 시작된 훈련 세션 정보. 카드의 진정 조건·양보 불가선은 담지 않는다.
 */
@Schema(description = "훈련 세션 정보")
public record TrainingSessionResponse(
		@Schema(description = "세션 식별자 — 발화 턴 제출 주소에 쓴다", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
		Long sessionId,
		@Schema(description = "고른 카드 식별자", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
		Long cardId,
		@Schema(description = "민원인 화면 표시 역할명 — 대화 화면의 상대 이름표", example = "손님",
				requiredMode = Schema.RequiredMode.REQUIRED)
		String complainantRoleName,
		@Schema(description = "강도(1~3)", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
		int intensity,
		@Schema(description = "하드 턴 상한 — 진행 표시(예: 3/12)에 쓴다", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
		int hardTurnLimit,
		@Schema(description = "세션 상태", example = "IN_PROGRESS", requiredMode = Schema.RequiredMode.REQUIRED)
		TrainingSessionStatus status,
		@Schema(description = "확정된 발화 턴 수(시작 직후 0)", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
		int turnCount,
		@Schema(description = "시작 시각", example = "2026-10-28T14:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
		LocalDateTime startedAt
) {

	/** 세션과 카드에서 만든다. */
	public static TrainingSessionResponse of(TrainingSession session, ScenarioCard card) {
		return new TrainingSessionResponse(
				session.getId(),
				card.getId(),
				card.getComplainantRoleName(),
				session.getIntensity(),
				card.getHardTurnLimit(),
				session.getStatus(),
				session.getTurnCount(),
				session.getStartedAt());
	}
}
