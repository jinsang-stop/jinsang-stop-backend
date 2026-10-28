package com.jinsangstop.growth;

import com.jinsangstop.scoring.AttitudeAxis;
import com.jinsangstop.scoring.AttitudeScores;
import com.jinsangstop.training.domain.ScoreStatus;
import com.jinsangstop.training.domain.TrainingSession;
import com.jinsangstop.training.domain.TrainingSessionStatus;
import com.jinsangstop.training.domain.UtteranceTurn;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * 성장 기록 편입 판정 — 리포트(#7)와 성장 기록 조회(#8)가 함께 쓰는 단 하나의 판정 로직.
 * 편입 조건: 진정 종료 AND 모든 발화 턴 채점됨 AND 경계 설정 평균 ≥ 하한선(PRD v2).
 * 하한선은 설정값(GROWTH_BOUNDARY_THRESHOLD)이고, 비어 있으면 모든 세션을 "편입 보류"로 둔다 — 임의 숫자를 코드에 박지 않는다.
 */
@Component
public class GrowthEligibility {

	/** 판정 결과 — 편입 여부, 미편입 사유(편입이면 빈 목록), 세 축 평균(채점된 턴 기준) */
	public record Result(boolean included, List<ExclusionReason> reasons, AxisAverages averages) {
	}

	/** 세 축 평균 — 채점된 턴이 없으면 null. 합산하지 않는다 */
	public record AxisAverages(Double emotionalStability, Double empathyAcknowledgement, Double boundarySetting) {

		public Double of(AttitudeAxis axis) {
			return switch (axis) {
				case EMOTIONAL_STABILITY -> emotionalStability;
				case EMPATHY_ACKNOWLEDGEMENT -> empathyAcknowledgement;
				case BOUNDARY_SETTING -> boundarySetting;
			};
		}
	}

	/** 경계 설정 하한선(1~5). 비어 있으면 미정 */
	private final Optional<Double> boundaryThreshold;

	public GrowthEligibility(@Value("${jinsangstop.growth.boundary-threshold:}") String boundaryThreshold) {
		this.boundaryThreshold = parse(boundaryThreshold);
	}

	/** 하한선이 정해졌는지 */
	public boolean isThresholdConfigured() {
		return boundaryThreshold.isPresent();
	}

	/** 하한선 값(미정이면 빈 값) */
	public Optional<Double> boundaryThreshold() {
		return boundaryThreshold;
	}

	/** 세션 하나를 판정한다. turns는 그 세션의 턴 전부다. */
	public Result evaluate(TrainingSession session, List<UtteranceTurn> turns) {
		List<ExclusionReason> reasons = new ArrayList<>();
		if (session.getStatus() != TrainingSessionStatus.CALMED) {
			reasons.add(ExclusionReason.NOT_CALMED);
		}
		boolean allScored = !turns.isEmpty() && turns.stream().allMatch(t -> t.getScoreStatus() == ScoreStatus.SCORED);
		if (!allScored) {
			reasons.add(ExclusionReason.UNSCORED_TURNS);
		}
		AxisAverages averages = averages(turns);
		if (boundaryThreshold.isEmpty()) {
			reasons.add(ExclusionReason.BOUNDARY_THRESHOLD_UNSET);
		} else if (averages.boundarySetting() != null && averages.boundarySetting() < boundaryThreshold.get()) {
			reasons.add(ExclusionReason.BOUNDARY_BELOW_THRESHOLD);
		}
		return new Result(reasons.isEmpty(), List.copyOf(reasons), averages);
	}

	/** 채점된 턴만으로 축별 평균 */
	static AxisAverages averages(List<UtteranceTurn> turns) {
		List<AttitudeScores> scored = turns.stream().flatMap(t -> t.scores().stream()).toList();
		return new AxisAverages(
				average(scored, AttitudeAxis.EMOTIONAL_STABILITY),
				average(scored, AttitudeAxis.EMPATHY_ACKNOWLEDGEMENT),
				average(scored, AttitudeAxis.BOUNDARY_SETTING));
	}

	private static Double average(List<AttitudeScores> scored, AttitudeAxis axis) {
		OptionalDouble avg = scored.stream().mapToInt(s -> s.of(axis).score()).average();
		return avg.isPresent() ? Math.round(avg.getAsDouble() * 100) / 100.0 : null;
	}

	private static Optional<Double> parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return Optional.empty();
		}
		double value = Double.parseDouble(raw.strip());
		if (value < AttitudeScores.MIN_SCORE || value > AttitudeScores.MAX_SCORE) {
			throw new IllegalStateException("경계 설정 하한선은 1~5 사이여야 한다: " + raw);
		}
		return Optional.of(value);
	}
}
