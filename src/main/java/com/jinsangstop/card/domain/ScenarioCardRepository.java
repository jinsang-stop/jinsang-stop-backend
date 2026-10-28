package com.jinsangstop.card.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 시나리오 카드 저장소. 카드는 읽기만 한다.
 */
public interface ScenarioCardRepository extends JpaRepository<ScenarioCard, Long> {

	/** 종류별 카드를 식별자 순으로 모두 가져온다. */
	List<ScenarioCard> findAllByCardTypeOrderByIdAsc(CardType cardType);

	/** 식별자와 종류가 모두 맞는 카드를 가져온다. */
	Optional<ScenarioCard> findByIdAndCardType(Long id, CardType cardType);
}
