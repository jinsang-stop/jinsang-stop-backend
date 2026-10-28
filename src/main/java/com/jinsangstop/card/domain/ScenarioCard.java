package com.jinsangstop.card.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 시나리오 카드 — 사람이 작성해 저장해 둔 훈련 단위(ADR-0003).
 * 애플리케이션은 카드를 만들거나 고치지 않으므로 setter를 두지 않는다.
 * 진정 조건과 양보 불가선은 민원인 연기 프롬프트 조립에만 쓰고 프론트 응답에는 싣지 않는다.
 */
@Entity
@Table(name = "scenario_card")
public class ScenarioCard {

	/** 카드 식별자 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 카드 종류(공식 카드 등) */
	@Enumerated(EnumType.STRING)
	@Column(name = "card_type", nullable = false, length = 20)
	private CardType cardType;

	/** 카드 이름 */
	@Column(nullable = false, length = 100)
	private String name;

	/** 상황 설정 */
	@Column(nullable = false, columnDefinition = "TEXT")
	private String situation;

	/** 민원인 화면 표시 역할명(손님 등) */
	@Column(name = "complainant_role_name", nullable = false, length = 30)
	private String complainantRoleName;

	/** 민원인 페르소나 */
	@Column(name = "complainant_persona", nullable = false, columnDefinition = "TEXT")
	private String complainantPersona;

	/** 민원인의 실제 요구 */
	@Column(name = "complainant_demand", nullable = false, columnDefinition = "TEXT")
	private String complainantDemand;

	/** 양보하면 안 되는 선 — 프론트에 내려가지 않는다 */
	@Column(name = "non_negotiable_line", nullable = false, columnDefinition = "TEXT")
	private String nonNegotiableLine;

	/** 진정 조건 — 프론트에 내려가지 않는다 */
	@Column(name = "calming_condition", nullable = false, columnDefinition = "TEXT")
	private String calmingCondition;

	/** 하드 턴 상한 */
	@Column(name = "hard_turn_limit", nullable = false)
	private int hardTurnLimit;

	/** 등록 시각 */
	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	/** JPA 전용 기본 생성자 */
	protected ScenarioCard() {
	}

	public Long getId() {
		return id;
	}

	public CardType getCardType() {
		return cardType;
	}

	public String getName() {
		return name;
	}

	public String getSituation() {
		return situation;
	}

	public String getComplainantRoleName() {
		return complainantRoleName;
	}

	public String getComplainantPersona() {
		return complainantPersona;
	}

	public String getComplainantDemand() {
		return complainantDemand;
	}

	public String getNonNegotiableLine() {
		return nonNegotiableLine;
	}

	public String getCalmingCondition() {
		return calmingCondition;
	}

	public int getHardTurnLimit() {
		return hardTurnLimit;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
