package com.jinsangstop.training.dto;

import com.jinsangstop.scoring.AttitudeScores;
import com.jinsangstop.training.domain.ScoreStatus;
import com.jinsangstop.training.domain.UtteranceTurn;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 발화 턴의 태도 3축 점수. 세 축을 합산한 종합 점수는 두지 않는다(ADR-0004).
 * 채점 중(PENDING)이거나 실패(FAILED)면 축 값이 모두 null이다 — 0점이 아니라 "정보 없음"이다.
 */
@Schema(description = "턴 점수 — 합산 점수 없음. PENDING·FAILED면 세 축이 모두 null(정보 없음)")
public record TurnScoreResponse(
		@Schema(description = "턴 번호", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
		int turnNumber,
		@Schema(description = "채점 상태 — PENDING(채점 중, 잠시 뒤 점수 조회 API로 다시 받는다) / SCORED / FAILED(정보 없음)",
				example = "SCORED", requiredMode = Schema.RequiredMode.REQUIRED)
		ScoreStatus status,
		@Schema(description = "정서 안정")
		AxisScoreResponse emotionalStability,
		@Schema(description = "공감 인정")
		AxisScoreResponse empathyAcknowledgement,
		@Schema(description = "경계 설정")
		AxisScoreResponse boundarySetting
) {

	/** 축 하나의 점수와 근거 */
	@Schema(description = "축 점수")
	public record AxisScoreResponse(
			@Schema(description = "점수(1~5 정수)", example = "4", minimum = "1", maximum = "5",
					requiredMode = Schema.RequiredMode.REQUIRED)
			int score,
			@Schema(description = "짧은 근거(80자 이내)", example = "도발에도 차분한 존댓말을 유지했다.",
					requiredMode = Schema.RequiredMode.REQUIRED)
			String reason
	) {
		static AxisScoreResponse from(AttitudeScores.AxisScore axis) {
			return new AxisScoreResponse(axis.score(), axis.reason());
		}
	}

	/** 턴 엔티티에서 만든다. */
	public static TurnScoreResponse from(UtteranceTurn turn) {
		return turn.scores()
				.map(s -> new TurnScoreResponse(turn.getTurnNumber(), ScoreStatus.SCORED,
						AxisScoreResponse.from(s.emotionalStability()),
						AxisScoreResponse.from(s.empathyAcknowledgement()),
						AxisScoreResponse.from(s.boundarySetting())))
				.orElseGet(() -> new TurnScoreResponse(turn.getTurnNumber(), turn.getScoreStatus(), null, null, null));
	}
}
