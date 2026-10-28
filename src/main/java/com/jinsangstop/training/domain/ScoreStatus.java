package com.jinsangstop.training.domain;

/**
 * 발화 턴의 채점 상태. 채점은 턴 응답보다 늦게 끝날 수 있다(슬라이스 #6 계약).
 */
public enum ScoreStatus {

	/** 채점 중 — 턴 응답이 먼저 나갔고 채점 결과를 기다린다 */
	PENDING,
	/** 세 축 모두 채점됨 */
	SCORED,
	/** 채점 실패 — 세 축 모두 정보 없음(0점이 아니다) */
	FAILED
}
