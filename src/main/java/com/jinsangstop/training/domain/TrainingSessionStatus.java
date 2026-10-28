package com.jinsangstop.training.domain;

/**
 * 훈련 세션 상태 — 진행 중이 아니면 그 값이 곧 종료 사유다. 진정 종료·하드 턴 상한·사용자 중단은 #6에서 추가한다.
 */
public enum TrainingSessionStatus {

	/** 진행 중 — 시연 장비 전체에서 하나만 있을 수 있다 */
	IN_PROGRESS,
	/** 오래 방치돼 자동으로 정리된 세션 — 채점·성장 기록 대상이 아니다 */
	ABANDONED,
	/** 시스템 오류로 중단 — 민원인 대사가 재시도까지 출력 검사를 통과하지 못함(ADR-0009) */
	SYSTEM_ERROR
}
