package com.jinsangstop.report.service;

import com.jinsangstop.card.domain.ScenarioCard;
import com.jinsangstop.card.domain.ScenarioCardRepository;
import com.jinsangstop.common.error.BusinessException;
import com.jinsangstop.common.error.ErrorCode;
import com.jinsangstop.growth.GrowthEligibility;
import com.jinsangstop.report.dto.SessionReportResponse;
import com.jinsangstop.training.domain.TrainingSession;
import com.jinsangstop.training.domain.TrainingSessionRepository;
import com.jinsangstop.training.domain.UtteranceTurn;
import com.jinsangstop.training.domain.UtteranceTurnRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 세션 리포트 — 턴별 전사·대사·점수, 종료 사유, 소요 턴 수, 성장 기록 편입 여부와 사유.
 * 편입 판정은 성장 기록 조회와 같은 GrowthEligibility를 쓴다(두 곳에 따로 구현하지 않는다).
 */
@Service
@Transactional(readOnly = true)
public class SessionReportService {

	private final TrainingSessionRepository trainingSessionRepository;
	private final UtteranceTurnRepository utteranceTurnRepository;
	private final ScenarioCardRepository scenarioCardRepository;
	private final GrowthEligibility growthEligibility;

	public SessionReportService(TrainingSessionRepository trainingSessionRepository,
			UtteranceTurnRepository utteranceTurnRepository, ScenarioCardRepository scenarioCardRepository,
			GrowthEligibility growthEligibility) {
		this.trainingSessionRepository = trainingSessionRepository;
		this.utteranceTurnRepository = utteranceTurnRepository;
		this.scenarioCardRepository = scenarioCardRepository;
		this.growthEligibility = growthEligibility;
	}

	/** 끝난 본인 세션의 리포트. 진행 중이면 409 */
	public SessionReportResponse getReport(Long userId, Long sessionId) {
		TrainingSession session = trainingSessionRepository.findByIdAndUserId(sessionId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.TRAINING_SESSION_NOT_FOUND));
		if (session.isInProgress()) {
			throw new BusinessException(ErrorCode.TRAINING_SESSION_IN_PROGRESS);
		}
		ScenarioCard card = scenarioCardRepository.findById(session.getCardId())
				.orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));
		List<UtteranceTurn> turns = utteranceTurnRepository.findAllBySessionIdOrderByTurnNumberAsc(sessionId);
		GrowthEligibility.Result eligibility = growthEligibility.evaluate(session, turns);

		return new SessionReportResponse(
				session.getId(),
				card.getId(),
				card.getName(),
				card.getComplainantRoleName(),
				session.getIntensity(),
				session.getStatus(),
				session.getTurnsToCalm(),
				session.getStartedAt(),
				session.getEndedAt(),
				turns.stream().map(SessionReportResponse.ReportTurn::from).toList(),
				SessionReportResponse.AxisAverages.from(eligibility.averages()),
				SessionReportResponse.GrowthRecordStatus.from(eligibility));
	}
}
