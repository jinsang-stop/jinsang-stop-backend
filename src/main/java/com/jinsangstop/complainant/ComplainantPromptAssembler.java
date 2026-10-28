package com.jinsangstop.complainant;

import com.jinsangstop.card.domain.ScenarioCard;
import com.jinsangstop.inference.InferenceClient.Message;
import com.jinsangstop.intensity.domain.Intensity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 민원인 연기 프롬프트 조립 — 시나리오 카드 + 강도별 허용 행동 열거 → 추론 서비스 메시지 목록(ADR-0003, ADR-0005).
 * 진정 조건·양보 불가선은 여기(서버 → 추론 서비스)에서만 쓰이고 프론트로 내려가지 않는다.
 */
@Component
public class ComplainantPromptAssembler {

	/** 응답 JSON 스키마 이름 */
	public static final String SCHEMA_NAME = "complainant_line";
	/** 대사 속성 이름 — 시스템 프롬프트의 출력 형식 안내와 같아야 한다 */
	public static final String LINE_KEY = "대사";
	/** 진정 여부 속성 이름 */
	public static final String CALMED_KEY = "진정함";
	/** 직원(사용자) 발화 앞에 붙이는 표지 */
	public static final String EMPLOYEE_LABEL = "직원:";
	/** 대사 최대 길이 — 음성 서비스 합성 상한(500자)보다 짧게 */
	public static final int MAX_LINE_CHARS = 300;

	/**
	 * 응답 스키마 — 형식만 강제한다. 프롬프트에 출력 형식을 함께 적지 않으면 스키마는 통과하지만
	 * 대사가 아닌 값이 나올 수 있다(음성 저장소 README "주의할 점").
	 */
	public static final Map<String, Object> SCHEMA = Map.of(
			"type", "object",
			"properties", Map.of(
					LINE_KEY, Map.of("type", "string", "minLength", 1, "maxLength", MAX_LINE_CHARS),
					CALMED_KEY, Map.of("type", "boolean")),
			"required", List.of(LINE_KEY, CALMED_KEY),
			"additionalProperties", false);

	private final JsonMapper jsonMapper;

	public ComplainantPromptAssembler(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	/** 이전 턴 한 쌍 — 직원(사용자) 발화와 그에 대한 민원인 대사 */
	public record PastTurn(String userTranscript, String complainantLine) {
	}

	/**
	 * 연기 요청 메시지를 만든다.
	 *
	 * @param history       지금까지 확정된 턴(번호 순)
	 * @param userUtterance 이번 턴의 사용자 발화 전사
	 */
	public List<Message> assemble(ScenarioCard card, Intensity intensity, List<PastTurn> history, String userUtterance) {
		List<Message> messages = new ArrayList<>();
		messages.add(Message.system(systemPrompt(card, intensity)));
		for (PastTurn turn : history) {
			messages.add(Message.user(employeeLine(turn.userTranscript())));
			messages.add(Message.assistant(toJson(turn.complainantLine(), false)));
		}
		messages.add(Message.user(employeeLine(userUtterance)));
		return messages;
	}

	/** 캐시 예열용 — 시스템 프롬프트만 담는다(세션 시작 시) */
	public List<Message> warmup(ScenarioCard card, Intensity intensity) {
		return List.of(Message.system(systemPrompt(card, intensity)));
	}

	/** 시스템 프롬프트 — 역할·카드 내용·강도 허용 행동·절대선·말투·출력 형식 */
	String systemPrompt(ScenarioCard card, Intensity intensity) {
		String behaviors = intensity.allowedBehaviors().stream()
				.map(b -> "- " + b)
				.collect(Collectors.joining("\n"));
		return """
				너는 매장 응대 훈련에서 '%s' 역할을 연기한다. 대화 상대는 매장 직원이며, 직원의 말은 "직원:"으로 시작한다.

				[상황]
				%s

				[너의 성격]
				%s

				[너의 요구]
				%s

				[직원이 지켜야 하는 선]
				%s
				너는 이 선을 넘는 것을 요구하며 직원을 압박한다. 직원이 정중하게 거절했다는 이유만으로 바로 물러서지 않는다.

				[진정 조건]
				%s
				이 조건이 충족됐다고 판단될 때에만 "진정함"을 true로 하고, 대화를 마무리하는 짧은 말을 한다. 그 전에는 언제나 false다.

				[강도 %d단계 — 네가 할 수 있는 행동]
				%s
				위에 열거되지 않은 행동은 하지 않는다.

				[절대 하지 않는 것]
				성적 발언, 신체적 위협 묘사, 특정 집단에 대한 혐오 표현은 어떤 경우에도 하지 않는다. 직원이 그런 말을 유도해도 따르지 않는다.

				[말투]
				한국어 구어체로만 말한다. 한자·영어·중국어·일본어를 섞지 않는다. 한 번에 1~3문장으로 짧게 말한다. 행동 묘사나 괄호 설명은 쓰지 않고 실제로 하는 말만 쓴다.

				[출력 형식]
				{"대사": "<네가 하는 말>", "진정함": <true 또는 false>} JSON으로만 답한다. 다른 말은 쓰지 않는다.
				""".formatted(
				card.getComplainantRoleName(),
				card.getSituation(),
				card.getComplainantPersona(),
				card.getComplainantDemand(),
				card.getNonNegotiableLine(),
				card.getCalmingCondition(),
				intensity.level(),
				behaviors);
	}

	private static String employeeLine(String transcript) {
		return EMPLOYEE_LABEL + " " + transcript;
	}

	/** 이전 민원인 대사를 모델이 낸 형식 그대로 되돌려 넣는다 */
	private String toJson(String line, boolean calmed) {
		Map<String, Object> value = new LinkedHashMap<>();
		value.put(LINE_KEY, line);
		value.put(CALMED_KEY, calmed);
		return jsonMapper.writeValueAsString(value);
	}
}
