package com.jinsangstop.training.service;

import com.jinsangstop.card.domain.CardType;
import com.jinsangstop.card.domain.ScenarioCard;
import com.jinsangstop.card.domain.ScenarioCardRepository;
import com.jinsangstop.common.config.AsyncConfig;
import com.jinsangstop.common.error.BusinessException;
import com.jinsangstop.common.error.ErrorCode;
import com.jinsangstop.complainant.ComplainantActor;
import com.jinsangstop.complainant.ComplainantActor.ComplainantReply;
import com.jinsangstop.complainant.ComplainantActor.OutputRejectedException;
import com.jinsangstop.complainant.ComplainantPromptAssembler.PastTurn;
import com.jinsangstop.inference.InferenceClient;
import com.jinsangstop.inference.InferenceServiceException;
import com.jinsangstop.intensity.domain.Intensity;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 훈련 세션 시작과 발화 턴 처리를 담당한다.
 * 턴 하나: 전사(음성 서비스) → 민원인 연기(추론 서비스) → 출력 검사 → 합성(음성 서비스) → 저장.
 * 외부 호출(수 초)은 DB 트랜잭션 밖에서 하고, 결과를 저장하는 짧은 구간만 트랜잭션으로 묶는다.
 */
@Service
public class TrainingSessionService {

	private static final Logger log = LoggerFactory.getLogger(TrainingSessionService.class);

	/** 합성 음성 형식 — 음성 서비스는 16비트 PCM WAV를 돌려준다 */
	private static final String SYNTHESIZED_AUDIO_TYPE = "audio/wav";

	private final TrainingSessionRepository trainingSessionRepository;
	private final UtteranceTurnRepository utteranceTurnRepository;
	private final ScenarioCardRepository scenarioCardRepository;
	private final VoiceClient voiceClient;
	private final InferenceClient inferenceClient;
	private final ComplainantActor complainantActor;
	private final TransactionTemplate transactionTemplate;
	private final TaskExecutor turnExecutor;
	private final Clock clock;
	/** 이 시간 동안 아무 턴도 없으면 방치된 세션으로 보고 다음 시작 요청 때 정리한다 */
	private final Duration idleTimeout;
	/** 세션 시작 시 연기 프롬프트 캐시 예열 여부 */
	private final boolean warmupEnabled;

	public TrainingSessionService(TrainingSessionRepository trainingSessionRepository,
			UtteranceTurnRepository utteranceTurnRepository,
			ScenarioCardRepository scenarioCardRepository,
			VoiceClient voiceClient,
			InferenceClient inferenceClient,
			ComplainantActor complainantActor,
			TransactionTemplate transactionTemplate,
			@Qualifier(AsyncConfig.TURN_EXECUTOR) TaskExecutor turnExecutor,
			Clock clock,
			@Value("${jinsangstop.training.idle-timeout}") Duration idleTimeout,
			@Value("${jinsangstop.acting.warmup-enabled}") boolean warmupEnabled) {
		this.trainingSessionRepository = trainingSessionRepository;
		this.utteranceTurnRepository = utteranceTurnRepository;
		this.scenarioCardRepository = scenarioCardRepository;
		this.voiceClient = voiceClient;
		this.inferenceClient = inferenceClient;
		this.complainantActor = complainantActor;
		this.transactionTemplate = transactionTemplate;
		this.turnExecutor = turnExecutor;
		this.clock = clock;
		this.idleTimeout = idleTimeout;
		this.warmupEnabled = warmupEnabled;
	}

	/**
	 * 훈련 세션을 시작한다.
	 * 1) 카드 확인 → 2) 음성·추론 서비스 준비 확인 → 3) 진행 중 세션이 없을 때만 생성 → 4) 연기 프롬프트 캐시 예열(비동기).
	 */
	public TrainingSessionResponse start(Long userId, Long cardId, int intensityLevel) {
		ScenarioCard card = scenarioCardRepository.findByIdAndCardType(cardId, CardType.OFFICIAL)
				.orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));
		Intensity intensity = toIntensity(intensityLevel);

		// 음성 서비스와 추론 서비스가 둘 다 준비돼야 온전한 훈련 세션이다
		if (!voiceClient.isReady() || !inferenceClient.isReady()) {
			throw new BusinessException(ErrorCode.TRAINING_ENVIRONMENT_NOT_READY);
		}

		long actingSeed = ThreadLocalRandom.current().nextLong(1, Integer.MAX_VALUE);
		TrainingSession session;
		try {
			session = transactionTemplate.execute(status -> {
				LocalDateTime now = now();
				releaseIdleSessionOrRejectIfBusy(now);
				return trainingSessionRepository.saveAndFlush(
						TrainingSession.start(userId, cardId, intensityLevel, actingSeed, now));
			});
		} catch (DataIntegrityViolationException e) {
			// 두 요청이 동시에 들어와 둘 다 빈 자리를 본 경우 — active_slot 유니크 제약이 하나만 통과시킨다
			throw new BusinessException(ErrorCode.TRAINING_SESSION_BUSY);
		}

		if (warmupEnabled) {
			// 사용자가 첫 마디를 하는 동안 슬롯 0에 카드·강도 프롬프트를 올려 둔다(ADR-0009)
			try {
				turnExecutor.execute(() -> complainantActor.warmup(card, intensity, actingSeed));
			} catch (TaskRejectedException e) {
				// 세션은 이미 만들어졌다 — 예열은 성능 최적화일 뿐이므로 실패해도 시작을 막지 않는다
				log.warn("연기 프롬프트 캐시 예열을 실행기에 올리지 못했다(무시): {}", e.getMessage());
			}
		}
		return TrainingSessionResponse.of(session, card);
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
	 * 발화 턴을 처리한다.
	 * 전사가 비면 턴을 소모하지 않는다. 외부 서비스 장애면 턴을 소모하지 않고 503 — 사용자가 다시 시도할 수 있다.
	 * 민원인 대사가 재시도까지 출력 검사를 통과하지 못하면 세션을 "시스템 오류로 중단"으로 끝낸다.
	 * 사용자 오디오와 합성 음성은 이 메서드 안에서만 존재하며 어디에도 저장하지 않는다(ADR-0006).
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
		ScenarioCard card = scenarioCardRepository.findById(session.getCardId())
				.orElseThrow(() -> new BusinessException(ErrorCode.CARD_NOT_FOUND));
		Intensity intensity = toIntensity(session.getIntensity());

		long startedNanos = System.nanoTime();

		// 1) 전사
		String transcript = transcribe(audio, contentType).strip();
		long transcribedNanos = System.nanoTime();
		// 빈 전사는 턴을 소모하지 않는다 — 턴 번호도, 소요 턴 수 계산용 turn_count도 그대로다
		if (transcript.isEmpty()) {
			throw new BusinessException(ErrorCode.EMPTY_TRANSCRIPT);
		}

		// 2) 민원인 연기 + 출력 검사
		int turnNumber = session.getTurnCount() + 1;
		List<PastTurn> history = utteranceTurnRepository.findAllBySessionIdOrderByTurnNumberAsc(sessionId).stream()
				.map(t -> new PastTurn(t.getUserTranscript(), t.getComplainantLine()))
				.toList();
		ComplainantReply reply = act(session, card, intensity, history, transcript, turnNumber);
		long actedNanos = System.nanoTime();

		// 3) 합성 — 출력 검사를 통과한 대사만 여기까지 온다
		byte[] speech = synthesize(reply.line());
		long synthesizedNanos = System.nanoTime();

		// 4) 저장
		int confirmedTurn = transactionTemplate.execute(status -> {
			TrainingSession locked = trainingSessionRepository.findByIdForUpdate(sessionId)
					.orElseThrow(() -> new BusinessException(ErrorCode.TRAINING_SESSION_NOT_FOUND));
			// 처리하는 사이 세션이 끝났을 수 있으므로 잠근 뒤 다시 본다 — 끝났으면 이 턴 결과는 버린다
			if (!locked.isInProgress()) {
				throw new BusinessException(ErrorCode.TRAINING_SESSION_NOT_IN_PROGRESS);
			}
			LocalDateTime now = now();
			int number = locked.confirmTurn(now);
			utteranceTurnRepository.save(UtteranceTurn.of(sessionId, number, transcript, reply.line(), reply.calmed(), now));
			return number;
		});

		// 턴 지연 측정 로그 — "말하기 끝내기" 요청 도착부터 민원인 음성 반환까지(서버 구간)
		log.info("턴 지연: sessionId={}, turn={}, totalMs={}, transcribeMs={}, actMs={}, actAttempts={}, synthesizeMs={}",
				sessionId, confirmedTurn, millis(startedNanos, System.nanoTime()), millis(startedNanos, transcribedNanos),
				millis(transcribedNanos, actedNanos), reply.attempts(), millis(actedNanos, synthesizedNanos));

		return new UtteranceTurnResponse(sessionId, confirmedTurn, transcript, reply.line(), reply.calmed(),
				Base64.getEncoder().encodeToString(speech), SYNTHESIZED_AUDIO_TYPE);
	}

	/** 민원인 연기. 출력 검사 재시도 실패 → 세션 종료(시스템 오류), 추론 서비스 장애 → 503(턴 미소모). */
	private ComplainantReply act(TrainingSession session, ScenarioCard card, Intensity intensity,
			List<PastTurn> history, String transcript, int turnNumber) {
		try {
			return complainantActor.reply(card, intensity, history, transcript, session.getActingSeed(), turnNumber);
		} catch (OutputRejectedException e) {
			endWithSystemError(session.getId());
			throw new BusinessException(ErrorCode.SESSION_ENDED_BY_SYSTEM_ERROR);
		} catch (InferenceServiceException e) {
			log.warn("민원인 연기 호출 실패: sessionId={}, message={}", session.getId(), e.getMessage());
			throw new BusinessException(ErrorCode.TRAINING_ENVIRONMENT_NOT_READY);
		}
	}

	/** 세션을 시스템 오류로 끝낸다 — 진행 중 자리도 함께 비운다. */
	private void endWithSystemError(Long sessionId) {
		transactionTemplate.executeWithoutResult(status -> trainingSessionRepository.findByIdForUpdate(sessionId)
				.filter(TrainingSession::isInProgress)
				.ifPresent(locked -> {
					locked.endWithSystemError(now());
					log.warn("훈련 세션 시스템 오류 종료: sessionId={}", sessionId);
				}));
	}

	/** 음성 서비스 전사 실패를 사용자에게 보일 오류 코드로 바꾼다. */
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

	/** 합성 실패는 환경 문제 — 턴을 소모하지 않고 503 */
	private byte[] synthesize(String line) {
		try {
			return voiceClient.synthesize(line);
		} catch (VoiceServiceException e) {
			log.warn("합성 실패: message={}", e.getMessage());
			throw new BusinessException(ErrorCode.TRAINING_ENVIRONMENT_NOT_READY);
		}
	}

	private static Intensity toIntensity(int level) {
		return Intensity.fromLevel(level).orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REQUEST));
	}

	/** 현재 시각(초 단위) — DB TIMESTAMP 정밀도와 맞춰 응답과 저장값이 같게 한다 */
	private LocalDateTime now() {
		return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
	}

	private static long millis(long fromNanos, long toNanos) {
		return (toNanos - fromNanos) / 1_000_000;
	}
}
