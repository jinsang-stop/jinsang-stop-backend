package com.jinsangstop.training.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 발화 턴 저장소.
 */
public interface UtteranceTurnRepository extends JpaRepository<UtteranceTurn, Long> {

	/** 세션의 턴을 번호 순으로 가져온다. */
	List<UtteranceTurn> findAllBySessionIdOrderByTurnNumberAsc(Long sessionId);

	/** 세션의 특정 턴 */
	Optional<UtteranceTurn> findBySessionIdAndTurnNumber(Long sessionId, int turnNumber);
}
