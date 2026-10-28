package com.jinsangstop.training.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 훈련 세션 — 사용자가 시나리오 카드 하나를 골라 시작한 한 번의 음성 대화 전체.
 * 진행 중인 세션은 active_slot = 1을 갖고, 유니크 제약으로 동시에 하나만 존재한다.
 */
@Entity
@Table(name = "training_session")
public class TrainingSession {

	/** 진행 중 표시값 — 끝나면 NULL로 비워 다음 세션이 들어올 수 있게 한다 */
	private static final int ACTIVE = 1;

	/** 세션 식별자 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 세션을 연 사용자 */
	@Column(name = "user_id", nullable = false)
	private Long userId;

	/** 고른 시나리오 카드 */
	@Column(name = "card_id", nullable = false)
	private Long cardId;

	/** 강도(1~3) */
	@Column(nullable = false)
	private int intensity;

	/** 상태 */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TrainingSessionStatus status;

	/** 진행 중이면 1, 아니면 NULL */
	@Column(name = "active_slot")
	private Integer activeSlot;

	/** 확정된 발화 턴 수 — 빈 전사는 세지 않는다 */
	@Column(name = "turn_count", nullable = false)
	private int turnCount;

	/** 시작 시각 */
	@Column(name = "started_at", nullable = false)
	private LocalDateTime startedAt;

	/** 마지막 활동 시각(시작 또는 마지막 턴 확정) */
	@Column(name = "last_activity_at", nullable = false)
	private LocalDateTime lastActivityAt;

	/** 종료 시각 */
	@Column(name = "ended_at")
	private LocalDateTime endedAt;

	/** 소요 턴 수 — 진정 종료일 때만 기록한다(ADR-0004) */
	@Column(name = "turns_to_calm")
	private Integer turnsToCalm;

	/** 민원인 연기 호출 시드 — 세션마다 기록해 대화를 재현할 수 있게 한다(ADR-0009) */
	@Column(name = "acting_seed")
	private Long actingSeed;

	/** JPA 전용 기본 생성자 */
	protected TrainingSession() {
	}

	private TrainingSession(Long userId, Long cardId, int intensity, long actingSeed, LocalDateTime now) {
		this.userId = userId;
		this.actingSeed = actingSeed;
		this.cardId = cardId;
		this.intensity = intensity;
		this.status = TrainingSessionStatus.IN_PROGRESS;
		this.activeSlot = ACTIVE;
		this.turnCount = 0;
		this.startedAt = now;
		this.lastActivityAt = now;
	}

	/** 새 진행 중 세션을 만든다. */
	public static TrainingSession start(Long userId, Long cardId, int intensity, long actingSeed, LocalDateTime now) {
		return new TrainingSession(userId, cardId, intensity, actingSeed, now);
	}

	/** 진행 중인지 */
	public boolean isInProgress() {
		return status == TrainingSessionStatus.IN_PROGRESS;
	}

	/** 마지막 활동 뒤로 idleTimeout 이상 지났는지 — 방치된 세션 판별 */
	public boolean isIdleLongerThan(Duration idleTimeout, LocalDateTime now) {
		return lastActivityAt.plus(idleTimeout).isBefore(now);
	}

	/** 발화 턴 하나를 확정하고 새 턴 번호를 돌려준다. */
	public int confirmTurn(LocalDateTime now) {
		this.turnCount += 1;
		this.lastActivityAt = now;
		return turnCount;
	}

	/**
	 * 턴을 확정한 직후 종료 여부를 판정한다.
	 * 진정했으면 진정 종료(소요 턴 수 = 이번 턴 번호), 아니면 하드 턴 상한에 닿았는지 본다.
	 *
	 * @return 이번 판정으로 세션이 끝났는지
	 */
	public boolean judgeEnd(boolean calmed, int hardTurnLimit, LocalDateTime now) {
		if (calmed) {
			this.turnsToCalm = turnCount;
			end(TrainingSessionStatus.CALMED, now);
			return true;
		}
		if (turnCount >= hardTurnLimit) {
			end(TrainingSessionStatus.TURN_LIMIT_REACHED, now);
			return true;
		}
		return false;
	}

	/** 사용자가 중단한다. */
	public void abort(LocalDateTime now) {
		end(TrainingSessionStatus.ABORTED, now);
	}

	/** 방치된 세션을 정리한다 — 진행 중 자리를 비운다. */
	public void abandon(LocalDateTime now) {
		end(TrainingSessionStatus.ABANDONED, now);
	}

	/** 시스템 오류로 중단한다. */
	public void endWithSystemError(LocalDateTime now) {
		end(TrainingSessionStatus.SYSTEM_ERROR, now);
	}

	/** 세션을 끝낸다 — 종료 사유(상태)를 하나만 기록하고 진행 중 자리를 비워 다음 세션이 시작될 수 있게 한다. */
	private void end(TrainingSessionStatus reason, LocalDateTime now) {
		this.status = reason;
		this.activeSlot = null;
		this.endedAt = now;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public Long getCardId() {
		return cardId;
	}

	public int getIntensity() {
		return intensity;
	}

	public TrainingSessionStatus getStatus() {
		return status;
	}

	public int getTurnCount() {
		return turnCount;
	}

	public LocalDateTime getStartedAt() {
		return startedAt;
	}

	public LocalDateTime getLastActivityAt() {
		return lastActivityAt;
	}

	public LocalDateTime getEndedAt() {
		return endedAt;
	}

	public Integer getTurnsToCalm() {
		return turnsToCalm;
	}

	public Long getActingSeed() {
		return actingSeed;
	}
}
