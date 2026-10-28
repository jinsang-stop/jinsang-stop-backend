package com.jinsangstop.scoring;

import com.jinsangstop.inference.InferenceClient;
import com.jinsangstop.inference.InferenceClient.ChatRequest;
import com.jinsangstop.scoring.AttitudeScores.AxisScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

/**
 * 태도 3축 채점 — 추론 서비스 슬롯 1, temperature 0, 고정 시드로 불러 같은 발화에 같은 점수가 나오게 한다(ADR-0009).
 * 응답의 범위·필수 값을 다시 검증하고, 실패하면 빈 값(= 정보 없음)을 돌려준다. 0점으로 대신하지 않는다.
 * 예외를 던지지 않는다 — 채점 실패가 턴 진행을 막지 않는다.
 */
@Component
public class AttitudeScorer {

	private static final Logger log = LoggerFactory.getLogger(AttitudeScorer.class);

	/** 채점 전용 슬롯 — 연기(0번)와 컨텍스트를 분리한다 */
	public static final int SCORING_SLOT = 1;
	/** 채점은 무작위성을 없앤다 */
	public static final double SCORING_TEMPERATURE = 0.0;

	private final InferenceClient inferenceClient;
	private final ScoringPromptAssembler promptAssembler;
	private final JsonMapper jsonMapper;
	private final long seed;
	private final int maxTokens;

	public AttitudeScorer(InferenceClient inferenceClient, ScoringPromptAssembler promptAssembler, JsonMapper jsonMapper,
			@Value("${jinsangstop.scoring.seed}") long seed,
			@Value("${jinsangstop.scoring.max-tokens}") int maxTokens) {
		this.inferenceClient = inferenceClient;
		this.promptAssembler = promptAssembler;
		this.jsonMapper = jsonMapper;
		this.seed = seed;
		this.maxTokens = maxTokens;
	}

	/**
	 * 직원 발화 하나를 채점한다.
	 *
	 * @return 세 축 모두 유효하면 점수, 하나라도 문제가 있거나 호출이 실패하면 빈 값(정보 없음)
	 */
	public Optional<AttitudeScores> score(String situation, String previousComplainantLine, String userUtterance) {
		try {
			String content = inferenceClient.complete(new ChatRequest(
					promptAssembler.assemble(situation, previousComplainantLine, userUtterance),
					SCORING_SLOT, SCORING_TEMPERATURE, seed, maxTokens,
					ScoringPromptAssembler.SCHEMA_NAME, ScoringPromptAssembler.SCHEMA));
			Optional<AttitudeScores> parsed = parse(content);
			if (parsed.isEmpty()) {
				log.warn("채점 응답 검증 실패 — 정보 없음으로 저장");
			}
			return parsed;
		} catch (RuntimeException e) {
			log.warn("채점 호출 실패 — 정보 없음으로 저장: {}", e.getMessage());
			return Optional.empty();
		}
	}

	/** 기동 시 루브릭을 슬롯 1에 미리 올린다. 실패해도 무시한다 */
	public void warmup() {
		try {
			inferenceClient.complete(new ChatRequest(promptAssembler.warmup(), SCORING_SLOT, SCORING_TEMPERATURE,
					seed, 1, null, null));
			log.info("채점 루브릭 캐시 예열 완료");
		} catch (RuntimeException e) {
			log.warn("채점 루브릭 캐시 예열 실패(무시): {}", e.getMessage());
		}
	}

	/** 세 축 모두 1~5 정수 + 비어 있지 않은 근거여야 한다. 하나라도 어기면 빈 값 */
	Optional<AttitudeScores> parse(String content) {
		try {
			JsonNode root = jsonMapper.readTree(content);
			AxisScore emotional = axis(root, AttitudeAxis.EMOTIONAL_STABILITY);
			AxisScore empathy = axis(root, AttitudeAxis.EMPATHY_ACKNOWLEDGEMENT);
			AxisScore boundary = axis(root, AttitudeAxis.BOUNDARY_SETTING);
			if (emotional == null || empathy == null || boundary == null) {
				return Optional.empty();
			}
			return Optional.of(new AttitudeScores(emotional, empathy, boundary));
		} catch (RuntimeException e) {
			return Optional.empty();
		}
	}

	private static AxisScore axis(JsonNode root, AttitudeAxis axis) {
		JsonNode node = root.get(axis.schemaKey());
		if (node == null || !node.isObject()) {
			return null;
		}
		JsonNode score = node.get(ScoringPromptAssembler.SCORE_KEY);
		JsonNode reason = node.get(ScoringPromptAssembler.REASON_KEY);
		if (score == null || !score.isIntegralNumber() || reason == null || !reason.isString()) {
			return null;
		}
		int value = score.asInt();
		String text = reason.asString().strip();
		if (value < AttitudeScores.MIN_SCORE || value > AttitudeScores.MAX_SCORE || text.isEmpty()) {
			return null;
		}
		if (text.length() > ScoringPromptAssembler.MAX_REASON_CHARS) {
			text = text.substring(0, ScoringPromptAssembler.MAX_REASON_CHARS);
		}
		return new AxisScore(value, text);
	}
}
