package com.jinsangstop.card.domain;

/**
 * 시나리오 카드의 종류.
 * MVP는 공식 카드만 다룬다. 내 카드(카드 조각 조립)는 다음 버전이다(ADR-0007).
 */
public enum CardType {

	/** 운영이 작성·검수해 모든 사용자에게 제공하는 공식 카드 */
	OFFICIAL
}
