package com.jinsangstop.scoring;

import com.jinsangstop.common.config.AsyncConfig;
import com.jinsangstop.inference.InferenceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

/**
 * 서버가 뜨면 채점 루브릭을 슬롯 1에 미리 올린다(ADR-0009 "캐시 예열: 서버 기동 시 루브릭").
 * 추론 서비스가 아직 안 떠 있으면 건너뛴다 — 첫 채점 때 그냥 조금 느릴 뿐이다.
 */
@Component
public class ScoringWarmup {

	private static final Logger log = LoggerFactory.getLogger(ScoringWarmup.class);

	private final AttitudeScorer attitudeScorer;
	private final InferenceClient inferenceClient;
	private final TaskExecutor turnExecutor;
	private final boolean enabled;

	public ScoringWarmup(AttitudeScorer attitudeScorer, InferenceClient inferenceClient,
			@Qualifier(AsyncConfig.TURN_EXECUTOR) TaskExecutor turnExecutor,
			@Value("${jinsangstop.scoring.warmup-enabled}") boolean enabled) {
		this.attitudeScorer = attitudeScorer;
		this.inferenceClient = inferenceClient;
		this.turnExecutor = turnExecutor;
		this.enabled = enabled;
	}

	/** 기동 완료 후 비동기로 예열한다 — 기동을 늦추지 않는다 */
	@EventListener(ApplicationReadyEvent.class)
	public void warmupOnStartup() {
		if (!enabled) {
			return;
		}
		try {
			turnExecutor.execute(() -> {
				if (inferenceClient.isReady()) {
					attitudeScorer.warmup();
				}
			});
		} catch (TaskRejectedException e) {
			// 예열은 성능 최적화일 뿐 — 실패해도 기동을 막지 않는다
			log.warn("채점 루브릭 캐시 예열을 실행기에 올리지 못했다(무시): {}", e.getMessage());
		}
	}
}
