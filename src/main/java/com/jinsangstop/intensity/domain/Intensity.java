package com.jinsangstop.intensity.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 강도 — 사용자가 훈련 세션 시작 전에 고르는 민원인의 공격 수위(ADR-0005).
 * 단계마다 민원인에게 허용되는 행동을 명시적으로 열거한다. 열거되지 않은 행동은 하지 않는다.
 * 이 목록은 사용자 고지 문구이면서, 이후 민원인 연기 프롬프트 조립에서도 같은 값을 쓴다.
 * (문구는 검수 전 초안이다.)
 */
public enum Intensity {

	/** 1단계 — 기본값. 불만은 분명하지만 존댓말과 정상 음량을 유지한다. */
	LEVEL_1(1, "1단계 · 불만 표현", List.of(
			"불만과 요구를 분명하게 말한다",
			"같은 요구를 여러 번 되풀이한다",
			"존댓말과 평소 목소리 크기를 유지한다"
	), false),

	/** 2단계 — 언성과 반말, 비꼬기가 나온다. 시작 전 고지 확인이 필요하다. */
	LEVEL_2(2, "2단계 · 언성과 비꼬기", List.of(
			"1단계의 행동을 모두 한다",
			"언성을 높이고 반말을 섞는다",
			"직원의 말을 비꼬거나 무시하는 투로 말한다",
			"점장이나 책임자를 불러 달라고 요구한다"
	), true),

	/** 3단계 — 고성과 직원 능력에 대한 비하가 나온다. 시작 전 고지 확인이 필요하다. */
	LEVEL_3(3, "3단계 · 고성과 비하", List.of(
			"2단계의 행동을 모두 한다",
			"고성으로 말을 몰아붙인다",
			"직원의 업무 능력과 태도를 깎아내리는 말을 한다",
			"온라인 리뷰나 본사 민원으로 문제 삼겠다고 압박한다"
	), true);

	/** 기본 강도 단계 */
	public static final Intensity DEFAULT = LEVEL_1;

	/** 단계 숫자(1~3) */
	private final int level;
	/** 화면 표시용 단계 이름 */
	private final String title;
	/** 이 단계에서 민원인에게 허용되는 행동 목록 */
	private final List<String> allowedBehaviors;
	/** 시작 전 고지 확인이 필요한지 여부(2단계 이상) */
	private final boolean requiresConsent;

	Intensity(int level, String title, List<String> allowedBehaviors, boolean requiresConsent) {
		this.level = level;
		this.title = title;
		this.allowedBehaviors = allowedBehaviors;
		this.requiresConsent = requiresConsent;
	}

	/** 단계 숫자로 강도를 찾는다. 범위 밖이면 빈 값. */
	public static Optional<Intensity> fromLevel(int level) {
		return Arrays.stream(values()).filter(i -> i.level == level).findFirst();
	}

	public int level() {
		return level;
	}

	public String title() {
		return title;
	}

	public List<String> allowedBehaviors() {
		return allowedBehaviors;
	}

	public boolean requiresConsent() {
		return requiresConsent;
	}
}
