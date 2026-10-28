package com.jinsangstop.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

/**
 * 저장 세션 픽스처 — 리포트·성장 기록 테스트에서 외부 서비스 없이 끝난 세션과 턴을 DB에 바로 만든다.
 */
public final class SessionFixtures {

	private final JdbcTemplate jdbcTemplate;

	public SessionFixtures(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/** 로그인 아이디로 사용자 식별자를 찾는다. */
	public long userId(String loginId) {
		return jdbcTemplate.queryForObject("SELECT id FROM user_account WHERE login_id = ?", Long.class, loginId);
	}

	/**
	 * 끝난(또는 진행 중) 세션을 만든다. 진행 중이면 active_slot=1.
	 *
	 * @param turnsToCalm 진정 종료일 때만 값
	 */
	public long session(long userId, long cardId, int intensity, String status, Integer turnsToCalm, LocalDateTime endedAt) {
		boolean inProgress = status.equals("IN_PROGRESS");
		LocalDateTime startedAt = (endedAt == null ? LocalDateTime.of(2026, 10, 28, 14, 0) : endedAt.minusMinutes(5));
		jdbcTemplate.update("""
				INSERT INTO training_session (user_id, card_id, intensity, status, active_slot, turn_count, started_at,
				  last_activity_at, ended_at, acting_seed, turns_to_calm)
				VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?, 7, ?)""",
				userId, cardId, intensity, status, inProgress ? 1 : null, startedAt, startedAt, endedAt, turnsToCalm);
		return jdbcTemplate.queryForObject("SELECT MAX(id) FROM training_session", Long.class);
	}

	/** 채점된 턴을 만든다. */
	public void scoredTurn(long sessionId, int turnNumber, int emotional, int empathy, int boundary) {
		insertTurn(sessionId, turnNumber, "SCORED", emotional, empathy, boundary);
	}

	/** 채점 실패(정보 없음) 턴을 만든다. */
	public void failedTurn(long sessionId, int turnNumber) {
		insertTurn(sessionId, turnNumber, "FAILED", null, null, null);
	}

	private void insertTurn(long sessionId, int turnNumber, String scoreStatus, Integer emotional, Integer empathy,
			Integer boundary) {
		jdbcTemplate.update("""
				INSERT INTO utterance_turn (session_id, turn_number, user_transcript, complainant_line, calmed, created_at,
				  score_status, emotional_stability_score, emotional_stability_reason,
				  empathy_acknowledgement_score, empathy_acknowledgement_reason, boundary_setting_score, boundary_setting_reason)
				VALUES (?, ?, ?, ?, FALSE, ?, ?, ?, ?, ?, ?, ?, ?)""",
				sessionId, turnNumber, "직원 발화 " + turnNumber, "손님 대사 " + turnNumber, LocalDateTime.of(2026, 10, 28, 14, turnNumber),
				scoreStatus,
				emotional, emotional == null ? null : "근거",
				empathy, empathy == null ? null : "근거",
				boundary, boundary == null ? null : "근거");
		jdbcTemplate.update("UPDATE training_session SET turn_count = turn_count + 1 WHERE id = ?", sessionId);
	}
}
