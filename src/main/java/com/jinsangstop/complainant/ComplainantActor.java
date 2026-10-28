package com.jinsangstop.complainant;

import com.jinsangstop.card.domain.ScenarioCard;
import com.jinsangstop.complainant.ComplainantPromptAssembler.PastTurn;
import com.jinsangstop.complainant.safety.OutputChecker;
import com.jinsangstop.inference.InferenceClient;
import com.jinsangstop.inference.InferenceClient.ChatRequest;
import com.jinsangstop.inference.InferenceClient.Message;
import com.jinsangstop.intensity.domain.Intensity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * 민원인 연기 — 프롬프트를 조립해 추론 서비스(슬롯 0)를 부르고, 출력 검사를 통과한 대사만 돌려준다.
 * 점수를 매기지 않는다(채점 모듈과 컨텍스트 분리, ADR-0009).
 */
@Component
public class ComplainantActor {

	private static final Logger log = LoggerFactory.getLogger(ComplainantActor.class);

	/** 연기 전용 슬롯 — 채점(1번)과 섞이지 않게 고정한다. 서버가 범위를 검사하지 않으므로 여기서 맞춘다 */
	public static final int ACTING_SLOT = 0;
	/** 출력 검사에 실패하면 이 횟수만큼 다시 생성한다(처음 1회 + 재시도 1회) */
	private static final int MAX_ATTEMPTS = 2;

	private final InferenceClient inferenceClient;
	private final ComplainantPromptAssembler promptAssembler;
	private final OutputChecker outputChecker;
	private final JsonMapper jsonMapper;
	private final double temperature;
	private final int maxTokens;

	public ComplainantActor(InferenceClient inferenceClient, ComplainantPromptAssembler promptAssembler,
			OutputChecker outputChecker, JsonMapper jsonMapper,
			@Value("${jinsangstop.acting.temperature}") double temperature,
			@Value("${jinsangstop.acting.max-tokens}") int maxTokens) {
		this.inferenceClient = inferenceClient;
		this.promptAssembler = promptAssembler;
		this.outputChecker = outputChecker;
		this.jsonMapper = jsonMapper;
		this.temperature = temperature;
		this.maxTokens = maxTokens;
	}

	/** 검사를 통과한 민원인 응답 */
	public record ComplainantReply(String line, boolean calmed, int attempts) {
	}

	/**
	 * 민원인 대사를 만든다.
	 *
	 * @param sessionSeed 세션마다 기록한 시드. 재시도는 시드를 바꿔 다른 대사를 얻는다
	 * @throws OutputRejectedException 재시도까지 출력 검사를 통과하지 못함 → 세션을 시스템 오류로 끝낸다
	 * @throws com.jinsangstop.inference.InferenceServiceException 추론 서비스 장애
	 */
	public ComplainantReply reply(ScenarioCard card, Intensity intensity, List<PastTurn> history,
			String userUtterance, long sessionSeed, int turnNumber) {
		List<Message> messages = promptAssembler.assemble(card, intensity, history, userUtterance);
		String lastReason = null;
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			// 같은 세션·턴·시도는 같은 시드 → 세션 시드만 있으면 재현할 수 있다
			long seed = sessionSeed + (turnNumber * 10L) + attempt;
			String content = inferenceClient.complete(new ChatRequest(messages, ACTING_SLOT, temperature, seed,
					maxTokens, ComplainantPromptAssembler.SCHEMA_NAME, ComplainantPromptAssembler.SCHEMA));
			ParsedReply parsed = parse(content);
			if (parsed == null) {
				lastReason = "응답 형식 오류";
			} else {
				OutputChecker.Result result = outputChecker.check(parsed.line());
				if (result.passed()) {
					return new ComplainantReply(parsed.line(), parsed.calmed(), attempt);
				}
				lastReason = result.reason();
			}
			// 걸린 대사 원문은 로그에 남기지 않는다 — 금칙 표현을 로그로 퍼뜨리지 않기 위함
			log.warn("출력 검사 실패: turn={}, attempt={}, reason={}", turnNumber, attempt, lastReason);
		}
		throw new OutputRejectedException(lastReason);
	}

	/** 캐시 예열 — 시스템 프롬프트를 슬롯 0에 미리 올려 첫 턴 지연을 줄인다. 실패해도 무시한다 */
	public void warmup(ScenarioCard card, Intensity intensity, long sessionSeed) {
		try {
			inferenceClient.complete(new ChatRequest(promptAssembler.warmup(card, intensity), ACTING_SLOT,
					temperature, sessionSeed, 1, null, null));
		} catch (RuntimeException e) {
			log.warn("연기 프롬프트 캐시 예열 실패(무시): {}", e.getMessage());
		}
	}

	private record ParsedReply(String line, boolean calmed) {
	}

	/** 스키마 모양의 JSON에서 대사·진정 여부를 꺼낸다. 형식이 깨졌으면 null */
	private ParsedReply parse(String content) {
		try {
			JsonNode node = jsonMapper.readTree(content);
			JsonNode line = node.get(ComplainantPromptAssembler.LINE_KEY);
			JsonNode calmed = node.get(ComplainantPromptAssembler.CALMED_KEY);
			if (line == null || !line.isString() || calmed == null || !calmed.isBoolean()) {
				return null;
			}
			String text = line.asString().strip();
			if (text.isEmpty() || text.length() > ComplainantPromptAssembler.MAX_LINE_CHARS) {
				return null;
			}
			// 상대(직원) 표지를 그대로 따라 쓴 응답은 역할 혼동이다 — 민원인 목소리로 직원 말을 읽게 되므로 형식 오류로 본다
			if (text.replaceAll("\\s+", "").startsWith(ComplainantPromptAssembler.EMPLOYEE_LABEL)) {
				return null;
			}
			return new ParsedReply(text, calmed.asBoolean());
		} catch (JacksonException e) {
			return null;
		}
	}

	/** 재시도까지 출력 검사를 통과하지 못했다 */
	public static class OutputRejectedException extends RuntimeException {
		public OutputRejectedException(String reason) {
			super("출력 검사 재시도 실패: " + reason);
		}
	}
}
