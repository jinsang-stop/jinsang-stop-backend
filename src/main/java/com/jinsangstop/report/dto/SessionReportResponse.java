package com.jinsangstop.report.dto;

import com.jinsangstop.growth.ExclusionReason;
import com.jinsangstop.growth.GrowthEligibility;
import com.jinsangstop.training.domain.TrainingSessionStatus;
import com.jinsangstop.training.domain.UtteranceTurn;
import com.jinsangstop.training.dto.TurnScoreResponse;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 세션 리포트(슬라이스 #8 계약). 진정 조건·양보 불가선·"올바른 조치" 항목은 없다(ADR-0002).
 * 세 축을 합산한 종합 점수도 없다 — 축별 평균만 보조 지표로 준다(ADR-0004).
 */
@Schema(description = "세션 리포트 — 진정 조건·양보 불가선·합산 점수 없음")
public record SessionReportResponse(
		@Schema(description = "세션 식별자", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
		Long sessionId,
		@Schema(description = "카드 식별자 — 같은 카드 재도전·성장 기록 조회에 쓴다", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
		Long cardId,
		@Schema(description = "카드 이름", example = "영수증 없는 도시락 환불", requiredMode = Schema.RequiredMode.REQUIRED)
		String cardName,
		@Schema(description = "민원인 화면 표시 역할명", example = "손님", requiredMode = Schema.RequiredMode.REQUIRED)
		String complainantRoleName,
		@Schema(description = "강도(1~3)", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
		int intensity,
		@Schema(description = "종료 사유 — CALMED / TURN_LIMIT_REACHED / ABORTED / SYSTEM_ERROR / ABANDONED",
				example = "CALMED", requiredMode = Schema.RequiredMode.REQUIRED)
		TrainingSessionStatus endReason,
		@Schema(description = "소요 턴 수 — 진정 종료일 때만 값이 있다(성장의 주지표)", example = "3")
		Integer turnsToCalm,
		@Schema(description = "시작 시각", example = "2026-10-28T14:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
		LocalDateTime startedAt,
		@Schema(description = "종료 시각", example = "2026-10-28T14:04:30", requiredMode = Schema.RequiredMode.REQUIRED)
		LocalDateTime endedAt,
		@ArraySchema(arraySchema = @Schema(description = "턴 타임라인(턴 번호 순) — 어느 턴에서 어느 축이 떨어졌는지 표시에 쓴다"))
		List<ReportTurn> turns,
		@Schema(description = "축별 평균(채점된 턴 기준, 보조 지표). 합산하지 않는다", requiredMode = Schema.RequiredMode.REQUIRED)
		AxisAverages axisAverages,
		@Schema(description = "성장 기록 편입 여부와 미편입 사유", requiredMode = Schema.RequiredMode.REQUIRED)
		GrowthRecordStatus growthRecord
) {

	/** 리포트의 턴 한 줄 */
	@Schema(description = "리포트 턴")
	public record ReportTurn(
			@Schema(description = "턴 번호", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
			int turnNumber,
			@Schema(description = "사용자 발화 전사", example = "죄송합니다 손님", requiredMode = Schema.RequiredMode.REQUIRED)
			String userTranscript,
			@Schema(description = "민원인 대사", example = "죄송하면 다예요?", requiredMode = Schema.RequiredMode.REQUIRED)
			String complainantLine,
			@Schema(description = "민원인이 이 턴에서 진정했는지", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
			boolean calmed,
			@Schema(description = "축별 점수·근거, 또는 정보 없음(FAILED)", requiredMode = Schema.RequiredMode.REQUIRED)
			TurnScoreResponse score
	) {
		public static ReportTurn from(UtteranceTurn turn) {
			return new ReportTurn(turn.getTurnNumber(), turn.getUserTranscript(), turn.getComplainantLine(),
					turn.isCalmed(), TurnScoreResponse.from(turn));
		}
	}

	/** 축별 평균 */
	@Schema(description = "축별 평균(소수 둘째 자리) — 채점된 턴이 없으면 null")
	public record AxisAverages(
			@Schema(description = "정서 안정 평균", example = "4.33") Double emotionalStability,
			@Schema(description = "공감 인정 평균", example = "3.67") Double empathyAcknowledgement,
			@Schema(description = "경계 설정 평균", example = "4.0") Double boundarySetting
	) {
		public static AxisAverages from(GrowthEligibility.AxisAverages averages) {
			return new AxisAverages(averages.emotionalStability(), averages.empathyAcknowledgement(),
					averages.boundarySetting());
		}
	}

	/** 성장 기록 편입 상태 */
	@Schema(description = "성장 기록 편입 상태")
	public record GrowthRecordStatus(
			@Schema(description = "성장 기록에 들어갔는지", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
			boolean included,
			@ArraySchema(arraySchema = @Schema(description = "미편입 사유(편입이면 빈 배열). 여러 개가 함께 붙을 수 있다"))
			List<Reason> reasons
	) {
		public static GrowthRecordStatus from(GrowthEligibility.Result result) {
			return new GrowthRecordStatus(result.included(), result.reasons().stream().map(Reason::from).toList());
		}
	}

	/** 미편입 사유 하나 */
	@Schema(description = "미편입 사유")
	public record Reason(
			@Schema(description = "사유 코드 — NOT_CALMED / UNSCORED_TURNS / BOUNDARY_BELOW_THRESHOLD / BOUNDARY_THRESHOLD_UNSET",
					example = "NOT_CALMED", requiredMode = Schema.RequiredMode.REQUIRED)
			ExclusionReason code,
			@Schema(description = "사용자 표시용 문구", example = "진정 종료로 끝나지 않은 훈련은 성장 기록에 들어가지 않아요.",
					requiredMode = Schema.RequiredMode.REQUIRED)
			String message
	) {
		public static Reason from(ExclusionReason reason) {
			return new Reason(reason, reason.message());
		}
	}
}
