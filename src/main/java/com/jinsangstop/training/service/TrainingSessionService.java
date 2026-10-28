package com.jinsangstop.training.service;

import com.jinsangstop.card.domain.CardType;
import com.jinsangstop.card.domain.ScenarioCard;
import com.jinsangstop.card.domain.ScenarioCardRepository;
import com.jinsangstop.common.error.BusinessException;
import com.jinsangstop.common.error.ErrorCode;
import com.jinsangstop.training.domain.TrainingSession;
import com.jinsangstop.training.domain.TrainingSessionRepository;
import com.jinsangstop.training.domain.TrainingSessionStatus;
import com.jinsangstop.training.domain.UtteranceTurn;
import com.jinsangstop.training.domain.UtteranceTurnRepository;
import com.jinsangstop.training.dto.TrainingSessionResponse;
import com.jinsangstop.training.dto.UtteranceTurnResponse;
import com.jinsangstop.voice.VoiceClient;
import com.jinsangstop.voice.VoiceServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * 훈련 세션 시작과 발화 턴 제출을 담당한다.
 * 음성 서비스 호출(수 초)은 DB 트랜잭션 밖에서 하고, 결과를 저장하는 짧은 구간만 트랜잭션으로 묶는다.
 */
@Service
public class TrainingSessionService {

	private static final Logger log = LoggerFactory.getLogger(TrainingSessionService.class);

	private final TrainingSessionRepository trainingSessionRepository;
	private final UtteranceTurnRepository utteranceTurnRepository;
	private final ScenarioCardRepository scenarioCardRepository;
	private final VoiceClient voiceClient;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;
	/** 이 시간 동안 아무 턴도 없으면 방치된 세션으로 보고 다음 시작 요청 때 정리한다 */
	private final Duration idleTimeout;

	public TrainingSessionService(TrainingSessionRepository trainingSessionRepository,
			UtteranceTurnRepository utteranceTurnRepository,
			ScenarioCardRepository scenarioCardRepository,
			VoiceClient voiceClient,
			TransactionTemplate transactionTemplate,
			Clock clock,
			@Value("${jinsangstop.training.idle-timeout}") Duration idleTimeout) {
		this.trainingSessionRepository = trainingSessionRepository;
		this.utteranceTurnRepository = utteranceTurnRepository;
		this.scenarioCardRepository = scenarioCardRepository;
		this.voiceClient = voiceClient;
		this.transactionTemplate = transactionTemplate;
		this.clock = clock;
		this.idleTimeout = idleTimeout;
	}

	/**
	 * 훈련 세션을 시작한다.
	 * 1) 카드 확인 → 2) 음성 서비스 준비 확인 → 3) 진행 중 세션이 없을 때만 생성.
	 */
	public TrainingSessionResponse start(Long userId, Long cardId, int intensity) {
		ScenarioCard card = scenarioCardRepository.findByIdAndCardType(cardId, CardType.OFFICIAL)
				.orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));

		// 음성 서비스가 준비되지 않았으면 세션을 만들지 않는다
		if (!voiceClient.isReady()) {
			throw new BusinessException(ErrorCode.TRAINING_ENVIRONMENT_NOT_READY);
		}

		try {
			TrainingSession session = transactionTemplate.execute(status -> {
				LocalDateTime now = now();
				releaseIdleSessionOrRejectIfBusy(now);
				return trainingSessionRepository.saveAndFlush(TrainingSession.start(userId, cardId, intensity, now));
			});
			return TrainingSessionResponse.of(session, card);
		} catch (DataIntegrityViolationException e) {
			// 두 요청이 동시에 들어와 둘 다 빈 자리를 본 경우 — active_slot 유니크 제약이 하나만 통과시킨다
			throw new BusinessException(ErrorCode.TRAINING_SESSION_BUSY);
		}
	}

	/**
	 * 진행 중인 세션이 있으면 거절한다. 단, 오래 방치된 세션이면 정리하고 자리를 비운다.
	 */
	private void releaseIdleSessionOrRejectIfBusy(LocalDateTime now) {
		Optional<TrainingSession> active = trainingSessionRepository.findByStatus(TrainingSessionStatus.IN_PROGRESS);
		if (active.isEmpty()) {
			return;
		}
		TrainingSession session = active.get();
		if (!session.isIdleLongerThan(idleTimeout, now)) {
			throw new BusinessException(ErrorCode.TRAINING_SESSION_BUSY);
		}
		log.info("방치된 훈련 세션 정리: sessionId={}, lastActivityAt={}", session.getId(), session.getLastActivityAt());
		session.abandon(now);
		trainingSessionRepository.saveAndFlush(session);
	}

	/**
	 * 발화 턴을 제출한다 — 오디오를 전사하고, 전사가 비어 있지 않을 때만 턴 번호를 매겨 저장한다.
	 * 오디오는 이 메서드의 지역 변수로만 존재하며 어디에도 저장하지 않는다(ADR-0006).
	 */
	public UtteranceTurnResponse submitTurn(Long userId, Long sessionId, byte[] audio, String contentType) {
		TrainingSession session = trainingSessionRepository.findByIdAndUserId(sessionId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.TRAINING_SESSION_NOT_FOUND));
		if (!session.isInProgress()) {
			throw new BusinessException(ErrorCode.TRAINING_SESSION_NOT_IN_PROGRESS);
		}
		if (audio.length == 0) {
			throw new BusinessException(ErrorCode.EMPTY_TRANSCRIPT);
		}

		String transcript = transcribe(audio, contentType).strip();
		// 빈 전사는 턴을 소모하지 않는다 — 턴 번호도, 소요 턴 수 계산용 turn_count도 그대로다
		if (transcript.isEmpty()) {
			throw new BusinessException(ErrorCode.EMPTY_TRANSCRIPT);
		}

		return transactionTemplate.execute(status -> {
			TrainingSession locked = trainingSessionRepository.findByIdForUpdate(sessionId)
					.orElseThrow(() -> new BusinessException(ErrorCode.TRAINING_SESSION_NOT_FOUND));
			// 전사하는 사이 세션이 정리됐을 수 있으므로 잠근 뒤 다시 본다
			if (!locked.isInProgress()) {
				throw new BusinessException(ErrorCode.TRAINING_SESSION_NOT_IN_PROGRESS);
			}
			LocalDateTime now = now();
			int turnNumber = locked.confirmTurn(now);
			utteranceTurnRepository.save(UtteranceTurn.of(sessionId, turnNumber, transcript, now));
			return new UtteranceTurnResponse(sessionId, turnNumber, transcript);
		});
	}

	/** 현재 시각(초 단위) — DB TIMESTAMP 정밀도와 맞춰 응답과 저장값이 같게 한다 */
	private LocalDateTime now() {
		return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
	}

	/** 음성 서비스 실패를 사용자에게 보일 오류 코드로 바꾼다. */
	private String transcribe(byte[] audio, String contentType) {
		try {
			return voiceClient.transcribe(audio, contentType);
		} catch (VoiceServiceException e) {
			log.warn("전사 실패: kind={}, message={}", e.getKind(), e.getMessage());
			throw new BusinessException(switch (e.getKind()) {
				case AUDIO_UNREADABLE -> ErrorCode.AUDIO_UNREADABLE;
				case AUDIO_TOO_LARGE -> ErrorCode.AUDIO_TOO_LARGE;
				case UNAVAILABLE -> ErrorCode.TRAINING_ENVIRONMENT_NOT_READY;
			});
		}
	}
}
