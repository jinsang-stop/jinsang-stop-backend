package com.jinsangstop.growth.dto;

import com.jinsangstop.growth.GrowthEligibility;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 성장 기록 — 같은 사용자·카드·강도로 편입 조건을 만족한 세션들의 소요 턴 수 시계열(ADR-0004).
 * 강도가 다른 세션은 섞지 않는다. 합산 점수는 없다.
 */
@Schema(description = "성장 기록 — 한 카드·한 강도의 시계열")
public record GrowthRecordResponse(
		@Schema(description = "카드 식별자", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
		Long cardId,
		@Schema(description = "카드 이름", example = "영수증 없는 도시락 환불", requiredMode = Schema.RequiredMode.REQUIRED)
		String cardName,
		@Schema(description = "강도 — 이 시계열의 모든 점은 같은 강도다", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
		int intensity,
		@Schema(description = "경계 설정 하한선이 정해졌는지. false면 모든 세션이 편입 보류라 점이 없다 — 그 사실을 안내한다",
				example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
		boolean boundaryThresholdConfigured,
		@ArraySchema(arraySchema = @Schema(description = "그래프의 점 — 종료 시각 오름차순. 주지표는 turnsToCalm"))
		List<Point> points
) {

	/** 그래프의 점 하나 = 편입된 세션 하나 */
	@Schema(description = "성장 기록의 점")
	public record Point(
			@Schema(description = "세션 식별자 — 점을 누르면 리포트로", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
			Long sessionId,
			@Schema(description = "세션 종료 시각(x축)", example = "2026-10-28T15:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
			LocalDateTime endedAt,
			@Schema(description = "소요 턴 수(주지표, y축) — 작을수록 빨리 진정시켰다", example = "4", requiredMode = Schema.RequiredMode.REQUIRED)
			int turnsToCalm,
			@Schema(description = "정서 안정 평균(보조 지표)", example = "4.25", requiredMode = Schema.RequiredMode.REQUIRED)
			double emotionalStability,
			@Schema(description = "공감 인정 평균(보조 지표)", example = "3.5", requiredMode = Schema.RequiredMode.REQUIRED)
			double empathyAcknowledgement,
			@Schema(description = "경계 설정 평균(보조 지표)", example = "4.0", requiredMode = Schema.RequiredMode.REQUIRED)
			double boundarySetting
	) {
		public static Point of(Long sessionId, LocalDateTime endedAt, int turnsToCalm, GrowthEligibility.AxisAverages averages) {
			return new Point(sessionId, endedAt, turnsToCalm, averages.emotionalStability(),
					averages.empathyAcknowledgement(), averages.boundarySetting());
		}
	}
}
