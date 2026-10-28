package com.jinsangstop.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 테스트 사이 데이터 정리. 테스트 클래스들이 같은 인메모리 H2를 공유하므로 외래 키 순서대로 비운다.
 * 시드 데이터(공식 카드)는 건드리지 않는다.
 */
public final class TestDataCleaner {

	private TestDataCleaner() {
	}

	/** 발화 턴 → 훈련 세션 → 사용자 순으로 지운다. */
	public static void clearUserData(JdbcTemplate jdbcTemplate) {
		jdbcTemplate.update("DELETE FROM utterance_turn");
		jdbcTemplate.update("DELETE FROM training_session");
		jdbcTemplate.update("DELETE FROM user_account");
	}
}
