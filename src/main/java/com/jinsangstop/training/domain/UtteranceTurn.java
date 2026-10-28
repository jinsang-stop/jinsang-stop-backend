package com.jinsangstop.training.domain;

import com.jinsangstop.scoring.AttitudeScores;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 발화 턴 — 사용자 발화 하나와 그에 대한 민원인 응답 하나의 쌍(ADR-0001).
 * 사용자 전사와 민원인 대사(텍스트)만 담는다. 사용자 음성·합성 음성은 저장하지 않는다(ADR-0006).
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

	/** 민원인 대사 — 출력 검사를 통과한 것만 저장된다 */
	@Column(name = "complainant_line", columnDefinition = "TEXT")
	private String complainantLine;

	/** 민원인이 이 턴에서 진정했는지 */
	@Column(nullable = false)
	private boolean calmed;

	/** 확정 시각 */
	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	/** 채점 상태 — 실패하면 FAILED이고 점수는 NULL(정보 없음)이다 */
	@Enumerated(EnumType.STRING)
	@Column(name = "score_status", nullable = false, length = 20)
	private ScoreStatus scoreStatus = ScoreStatus.PENDING;

	/** 정서 안정 점수(1~5) */
	@Column(name = "emotional_stability_score")
	private Integer emotionalStabilityScore;

	@Column(name = "emotional_stability_reason", length = 200)
	private String emotionalStabilityReason;

	/** 공감 인정 점수(1~5) */
	@Column(name = "empathy_acknowledgement_score")
	private Integer empathyAcknowledgementScore;

	@Column(name = "empathy_acknowledgement_reason", length = 200)
	private String empathyAcknowledgementReason;

	/** 경계 설정 점수(1~5) */
	@Column(name = "boundary_setting_score")
	private Integer boundarySettingScore;

	@Column(name = "boundary_setting_reason", length = 200)
	private String boundarySettingReason;

	/** JPA 전용 기본 생성자 */
	protected UtteranceTurn() {
	}

	private UtteranceTurn(Long sessionId, int turnNumber, String userTranscript, String complainantLine,
			boolean calmed, LocalDateTime createdAt) {
		this.sessionId = sessionId;
		this.turnNumber = turnNumber;
		this.userTranscript = userTranscript;
		this.complainantLine = complainantLine;
		this.calmed = calmed;
		this.createdAt = createdAt;
	}

	/** 확정된 턴을 만든다 — 사용자 전사와 그에 대한 민원인 응답 한 쌍(ADR-0001). */
	public static UtteranceTurn of(Long sessionId, int turnNumber, String userTranscript, String complainantLine,
			boolean calmed, LocalDateTime createdAt) {
		return new UtteranceTurn(sessionId, turnNumber, userTranscript, complainantLine, calmed, createdAt);
	}

	/** 채점 결과를 기록한다. */
	public void applyScores(AttitudeScores scores) {
		this.scoreStatus = ScoreStatus.SCORED;
		this.emotionalStabilityScore = scores.emotionalStability().score();
		this.emotionalStabilityReason = scores.emotionalStability().reason();
		this.empathyAcknowledgementScore = scores.empathyAcknowledgement().score();
		this.empathyAcknowledgementReason = scores.empathyAcknowledgement().reason();
		this.boundarySettingScore = scores.boundarySetting().score();
		this.boundarySettingReason = scores.boundarySetting().reason();
	}

	/** 채점 실패 — 점수는 비워 둔다(0으로 채우지 않는다). */
	public void markScoringFailed() {
		this.scoreStatus = ScoreStatus.FAILED;
		this.emotionalStabilityScore = null;
		this.emotionalStabilityReason = null;
		this.empathyAcknowledgementScore = null;
		this.empathyAcknowledgementReason = null;
		this.boundarySettingScore = null;
		this.boundarySettingReason = null;
	}

	/** 채점 결과(있으면)를 돌려준다. SCORED가 아니면 빈 값 */
	public Optional<AttitudeScores> scores() {
		if (scoreStatus != ScoreStatus.SCORED) {
			return Optional.empty();
		}
		return Optional.of(new AttitudeScores(
				new AttitudeScores.AxisScore(emotionalStabilityScore, emotionalStabilityReason),
				new AttitudeScores.AxisScore(empathyAcknowledgementScore, empathyAcknowledgementReason),
				new AttitudeScores.AxisScore(boundarySettingScore, boundarySettingReason)));
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

	public String getComplainantLine() {
		return complainantLine;
	}

	public boolean isCalmed() {
		return calmed;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public ScoreStatus getScoreStatus() {
		return scoreStatus;
	}
}
