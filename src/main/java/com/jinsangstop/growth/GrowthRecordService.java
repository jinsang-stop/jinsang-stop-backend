package com.jinsangstop.growth;

import com.jinsangstop.card.domain.CardType;
import com.jinsangstop.card.domain.ScenarioCard;
import com.jinsangstop.card.domain.ScenarioCardRepository;
import com.jinsangstop.common.error.BusinessException;
import com.jinsangstop.common.error.ErrorCode;
import com.jinsangstop.growth.dto.GrowthRecordResponse;
import com.jinsangstop.training.domain.TrainingSession;
import com.jinsangstop.training.domain.TrainingSessionRepository;
import com.jinsangstop.training.domain.TrainingSessionStatus;
import com.jinsangstop.training.domain.UtteranceTurnRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 성장 기록 조회 — 사용자·카드·강도별로 편입 조건을 만족한 세션만 모은다.
 * 편입 판정은 리포트와 같은 GrowthEligibility를 쓴다.
 */
@Service
@Transactional(readOnly = true)
public class GrowthRecordService {

	private final TrainingSessionRepository trainingSessionRepository;
	private final UtteranceTurnRepository utteranceTurnRepository;
	private final ScenarioCardRepository scenarioCardRepository;
	private final GrowthEligibility growthEligibility;

	public GrowthRecordService(TrainingSessionRepository trainingSessionRepository,
			UtteranceTurnRepository utteranceTurnRepository, ScenarioCardRepository scenarioCardRepository,
			GrowthEligibility growthEligibility) {
		this.trainingSessionRepository = trainingSessionRepository;
		this.utteranceTurnRepository = utteranceTurnRepository;
		this.scenarioCardRepository = scenarioCardRepository;
		this.growthEligibility = growthEligibility;
	}

	/** 본인의 한 카드·한 강도 시계열 */
	public GrowthRecordResponse getGrowthRecord(Long userId, Long cardId, int intensity) {
		ScenarioCard card = scenarioCardRepository.findByIdAndCardType(cardId, CardType.OFFICIAL)
				.orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));

		// 진정 종료가 아니면 어차피 편입되지 않으므로 후보는 진정 종료 세션으로 좁힌다
		List<TrainingSession> candidates = trainingSessionRepository
				.findAllByUserIdAndCardIdAndIntensityAndStatusOrderByEndedAtAsc(userId, cardId, intensity,
						TrainingSessionStatus.CALMED);
		List<GrowthRecordResponse.Point> points = new ArrayList<>();
		for (TrainingSession session : candidates) {
			GrowthEligibility.Result result = growthEligibility.evaluate(session,
					utteranceTurnRepository.findAllBySessionIdOrderByTurnNumberAsc(session.getId()));
			if (result.included()) {
				points.add(GrowthRecordResponse.Point.of(session.getId(), session.getEndedAt(), session.getTurnsToCalm(),
						result.averages()));
			}
		}
		return new GrowthRecordResponse(card.getId(), card.getName(), intensity,
				growthEligibility.isThresholdConfigured(), points);
	}
}
