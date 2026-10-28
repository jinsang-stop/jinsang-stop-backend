package com.jinsangstop.card.service;

import com.jinsangstop.card.domain.CardType;
import com.jinsangstop.card.domain.ScenarioCardRepository;
import com.jinsangstop.card.dto.ScenarioCardSummaryResponse;
import com.jinsangstop.common.error.BusinessException;
import com.jinsangstop.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 공식 카드 조회 서비스. 프론트 공개용 요약 DTO로만 바꿔서 돌려준다.
 */
@Service
@Transactional(readOnly = true)
public class ScenarioCardService {

	private final ScenarioCardRepository scenarioCardRepository;

	public ScenarioCardService(ScenarioCardRepository scenarioCardRepository) {
		this.scenarioCardRepository = scenarioCardRepository;
	}

	/** 공식 카드 목록을 가져온다. */
	public List<ScenarioCardSummaryResponse> getOfficialCards() {
		return scenarioCardRepository.findAllByCardTypeOrderByIdAsc(CardType.OFFICIAL).stream()
				.map(ScenarioCardSummaryResponse::from)
				.toList();
	}

	/** 공식 카드 하나를 가져온다. 없으면 CARD_NOT_FOUND. */
	public ScenarioCardSummaryResponse getOfficialCard(Long cardId) {
		return scenarioCardRepository.findByIdAndCardType(cardId, CardType.OFFICIAL)
				.map(ScenarioCardSummaryResponse::from)
				.orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));
	}
}
