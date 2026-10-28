package com.jinsangstop.training.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 발화 턴 — 사용자 발화 하나와 그에 대한 민원인 응답 하나의 쌍(ADR-0001).
 * 지금은 사용자 전사만 담는다. 오디오는 저장하지 않는다(ADR-0006).
 */
@Entity
@Table(name = "utterance_turn")
public class UtteranceTurn {

	/** 턴 식별자 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 소속 세션 */
	@Column(name = "session_id", nullable = false)
	private Long sessionId;

	/** 턴 번호(1부터) */
	@Column(name = "turn_number", nullable = false)
	private int turnNumber;

	/** 사용자 발화 전사 */
	@Column(name = "user_transcript", nullable = false, columnDefinition = "TEXT")
	private String userTranscript;

	/** 확정 시각 */
	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	/** JPA 전용 기본 생성자 */
	protected UtteranceTurn() {
	}

	private UtteranceTurn(Long sessionId, int turnNumber, String userTranscript, LocalDateTime createdAt) {
		this.sessionId = sessionId;
		this.turnNumber = turnNumber;
		this.userTranscript = userTranscript;
		this.createdAt = createdAt;
	}

	/** 확정된 턴을 만든다. */
	public static UtteranceTurn of(Long sessionId, int turnNumber, String userTranscript, LocalDateTime createdAt) {
		return new UtteranceTurn(sessionId, turnNumber, userTranscript, createdAt);
	}

	public Long getId() {
		return id;
	}

	public Long getSessionId() {
		return sessionId;
	}

	public int getTurnNumber() {
		return turnNumber;
	}

	public String getUserTranscript() {
		return userTranscript;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
