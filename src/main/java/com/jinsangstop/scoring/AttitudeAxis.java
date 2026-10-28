package com.jinsangstop.scoring;

/**
 * 태도 3축 — 고정된 세 축(ADR-0002). 축을 늘리거나 줄이려면 ADR-0002를 먼저 뒤집어야 한다.
 */
public enum AttitudeAxis {

	/** 정서 안정 — 도발에 흔들리지 않고 일정한 톤을 유지한 정도 */
	EMOTIONAL_STABILITY("정서안정", "정서 안정"),
	/** 공감 인정 — 요구를 판단하기 전에 불편·감정을 먼저 언어로 받아준 정도 */
	EMPATHY_ACKNOWLEDGEMENT("공감인정", "공감 인정"),
	/** 경계 설정 — 해줄 수 없는 것을 공격적이지 않게 명확히 거절한 정도 */
	BOUNDARY_SETTING("경계설정", "경계 설정");

	/** 채점 응답 JSON의 속성 이름 */
	private final String schemaKey;
	/** 화면·프롬프트 표시 이름 */
	private final String displayName;

	AttitudeAxis(String schemaKey, String displayName) {
		this.schemaKey = schemaKey;
		this.displayName = displayName;
	}

	public String schemaKey() {
		return schemaKey;
	}

	public String displayName() {
		return displayName;
	}
}
