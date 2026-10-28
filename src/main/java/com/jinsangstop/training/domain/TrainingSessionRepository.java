package com.jinsangstop.training.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 훈련 세션 저장소.
 */
public interface TrainingSessionRepository extends JpaRepository<TrainingSession, Long> {

	/** 지금 진행 중인 세션(있으면 하나뿐이다). */
	Optional<TrainingSession> findByStatus(TrainingSessionStatus status);

	/** 사용자 본인의 세션만 가져온다 — 남의 세션은 없는 것처럼 다룬다. */
	Optional<TrainingSession> findByIdAndUserId(Long id, Long userId);

	/** 성장 기록 후보 — 같은 사용자·카드·강도·상태의 세션을 종료 시각 순으로 */
	List<TrainingSession> findAllByUserIdAndCardIdAndIntensityAndStatusOrderByEndedAtAsc(
			Long userId, Long cardId, int intensity, TrainingSessionStatus status);
	/** 사용자의 특정 상태 세션 — 진행 중 세션은 시연 장비 전체에서 하나뿐이므로 많아야 하나다. */
	Optional<TrainingSession> findByUserIdAndStatus(Long userId, TrainingSessionStatus status);

	/** 턴 번호를 매기기 위해 세션 행을 잠그고 가져온다 — 같은 세션에 턴이 동시에 들어와도 번호가 겹치지 않는다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from TrainingSession s where s.id = :id")
	Optional<TrainingSession> findByIdForUpdate(@Param("id") Long id);
}
