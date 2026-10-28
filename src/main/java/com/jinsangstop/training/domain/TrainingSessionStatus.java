package com.jinsangstop.training.domain;

/**
 * 훈련 세션 상태. 진정 종료·하드 턴 상한·중단 같은 종료 사유는 #6에서 추가한다.
 */
public enum TrainingSessionStatus {

	/** 진행 중 — 시연 장비 전체에서 하나만 있을 수 있다 */
	IN_PROGRESS,
	/** 오래 방치돼 자동으로 정리된 세션 — 채점·성장 기록 대상이 아니다 */
	ABANDONED
}
