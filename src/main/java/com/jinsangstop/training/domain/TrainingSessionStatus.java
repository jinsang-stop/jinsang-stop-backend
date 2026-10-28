package com.jinsangstop.training.domain;

/**
 * 훈련 세션 상태 — 진행 중이 아니면 그 값이 곧 종료 사유다. 세션마다 종료 사유는 하나만 기록된다.
 * 정상 종료는 진정 종료(CALMED) 하나뿐이고, 나머지는 리포트는 받되 성장 기록에 들어가지 않는다(PRD v2).
 */
public enum TrainingSessionStatus {

	/** 진행 중 — 시연 장비 전체에서 하나만 있을 수 있다 */
	IN_PROGRESS,
	/** 진정 종료 — 민원인이 진정 조건 충족을 판단해 스스로 끝냄. 소요 턴 수가 기록되는 유일한 사유 */
	CALMED,
	/** 진정시키지 못한 채 종료 — 하드 턴 상한 도달 */
	TURN_LIMIT_REACHED,
	/** 사용자 중단 */
	ABORTED,
	/** 오래 방치돼 자동으로 정리된 세션 — 채점·성장 기록 대상이 아니다 */
	ABANDONED,
	/** 시스템 오류로 중단 — 민원인 대사가 재시도까지 출력 검사를 통과하지 못함(ADR-0009) */
	SYSTEM_ERROR
}
