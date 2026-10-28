package com.jinsangstop.growth;

/**
 * 성장 기록 미편입 사유(슬라이스 #8 계약). 여러 개가 함께 붙을 수 있다.
 * 사용자가 "왜 내 기록에 안 남았는지" 알 수 있도록 리포트에 반드시 표시한다(ADR-0004).
 */
public enum ExclusionReason {

	/** 진정 종료가 아님 — 하드 턴 상한·중단·시스템 오류·방치 */
	NOT_CALMED("진정 종료로 끝나지 않은 훈련은 성장 기록에 들어가지 않아요."),
	/** 채점되지 않은(정보 없음·채점 중) 턴이 있음 */
	UNSCORED_TURNS("채점되지 않은 턴이 있어 성장 기록에 넣지 않았어요."),
	/** 경계 설정이 하한선 미만 — 요구를 들어줘 빨리 끝내는 경로를 막는 장치 */
	BOUNDARY_BELOW_THRESHOLD("경계 설정 점수가 기준에 못 미쳐 성장 기록에 넣지 않았어요. 요구를 들어줘서 빨리 끝내는 것은 성장으로 보지 않아요."),
	/** 경계 하한선이 아직 정해지지 않아 편입 보류 */
	BOUNDARY_THRESHOLD_UNSET("경계 설정 기준이 아직 정해지지 않아 성장 기록 편입을 보류했어요.");

	/** 사용자 표시용 문구 */
	private final String message;

	ExclusionReason(String message) {
		this.message = message;
	}

	public String message() {
		return message;
	}
}
