package com.jinsangstop.scoring;

/**
 * 한 발화 턴의 태도 3축 점수(1~5)와 축별 근거. 세 축은 서로 독립이며 합산하지 않는다.
 */
public record AttitudeScores(AxisScore emotionalStability, AxisScore empathyAcknowledgement, AxisScore boundarySetting) {

	/** 점수 범위 */
	public static final int MIN_SCORE = 1;
	public static final int MAX_SCORE = 5;

	/** 축 하나의 점수와 짧은 근거 */
	public record AxisScore(int score, String reason) {
	}

	/** 축별 값 */
	public AxisScore of(AttitudeAxis axis) {
		return switch (axis) {
			case EMOTIONAL_STABILITY -> emotionalStability;
			case EMPATHY_ACKNOWLEDGEMENT -> empathyAcknowledgement;
			case BOUNDARY_SETTING -> boundarySetting;
		};
	}
}
