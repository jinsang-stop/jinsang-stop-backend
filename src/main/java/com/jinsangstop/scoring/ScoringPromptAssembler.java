package com.jinsangstop.scoring;

import com.jinsangstop.inference.InferenceClient.Message;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 채점 루브릭·프롬프트 조립(ADR-0002).
 * 채점 모듈은 민원인이 이번 턴에 무엇을 말할지 모르고, 카드의 진정 조건·양보 불가선도 모른다 —
 * 직원의 발화 내용과 표현만 본다(ADR-0006: 억양·성량은 쓰지 않는다). 직무 조치의 옳고 그름은 채점하지 않는다.
 */
@Component
public class ScoringPromptAssembler {

	/** 응답 JSON 스키마 이름 */
	public static final String SCHEMA_NAME = "attitude_score";
	/** 축별 객체 안의 속성 이름 */
	public static final String SCORE_KEY = "점수";
	public static final String REASON_KEY = "근거";
	/** 근거 최대 길이 — 짧게 제한해 채점 지연을 줄인다(ADR-0009) */
	public static final int MAX_REASON_CHARS = 80;

	/** 루브릭 — 서버 기동 시 슬롯 1에 캐시 예열하는 고정 시스템 프롬프트 */
	static final String RUBRIC = """
			너는 매장 응대 훈련의 채점관이다. 화가 난 손님(민원인)에게 직원이 한 **이번 한 번의 말**을 태도 3축으로 채점한다.

			[채점 원칙]
			- 세 축은 서로 독립이다. 한 축의 점수가 다른 축 점수에 영향을 주지 않는다.
			- 직무 조치의 옳고 그름은 채점하지 않는다. 규정을 정확히 안내했는지, 약속해도 되는 것을 약속했는지, 상급자에게 넘겼어야 했는지는 점수에 반영하지 않는다.
			- 전사된 말의 내용과 표현만 본다. 목소리 크기·억양·말 속도는 알 수 없으므로 추측하지 않는다.
			- 점수는 1~5 정수다.

			[정서 안정]
			도발·고성·인신공격에 흔들려 말투가 격해지거나 무너지지 않고 일정한 톤을 유지한 정도.
			5: 도발에도 차분하고 정중한 말투를 끝까지 유지한다. 3: 대체로 차분하지만 날이 서거나 위축된 표현이 섞인다. 1: 맞받아치거나 비꼬거나 감정적으로 무너진다.
			말수가 적거나 대답을 피한 것은 정서 안정이 아니다 — 회피와 침묵은 가점하지 않는다(최대 2점).

			[공감 인정]
			요구를 판단하기 전에 손님이 느낀 불편·감정을 먼저 말로 받아준 정도.
			5: 손님이 겪은 불편과 감정을 구체적으로 짚어 인정한다. 3: 형식적인 사과나 짧은 인정만 있다. 1: 감정을 무시하거나 손님 탓을 한다.
			요구를 들어주는 것은 공감 인정이 아니다. 같은 사과를 반복하기만 하는 과잉 사과도 공감 인정이 아니다.

			[경계 설정]
			해줄 수 없는 것을 회피하거나 얼버무리지 않고, 공격적이지 않은 방식으로 명확히 거절한 정도.
			5: 안 되는 것을 분명히 말하고 가능한 대안을 제시한다. 3: 거절은 했지만 모호하거나 여지를 남긴다. 1: 부당한 요구를 그대로 들어주거나, 거절을 피해 얼버무린다.
			공감 없이 단호하기만 한 거절도 경계 설정에서는 가점한다. 이번 말에 거절할 대상이 없으면(인사·질문 등) 3점을 준다.

			[출력 형식]
			{"정서안정": {"점수": <1~5>, "근거": "<한 문장>"}, "공감인정": {"점수": <1~5>, "근거": "<한 문장>"}, "경계설정": {"점수": <1~5>, "근거": "<한 문장>"}} JSON으로만 답한다.
			근거는 한국어 한 문장, 80자 이내로 쓴다.
			""";

	/** 응답 스키마 — 형식만 강제한다. 범위·필수 값 검증은 AttitudeScorer가 다시 한다 */
	public static final Map<String, Object> SCHEMA = schema();

	/**
	 * 채점 요청 메시지.
	 *
	 * @param situation              카드의 상황 설정(공개 정보) — 대화 맥락
	 * @param previousComplainantLine 직전 민원인 대사. 첫 턴이면 null
	 * @param userUtterance          채점할 직원 발화
	 */
	public List<Message> assemble(String situation, String previousComplainantLine, String userUtterance) {
		String context = previousComplainantLine == null
				? "(대화 시작 — 손님이 아직 말하지 않았다)"
				: previousComplainantLine;
		return List.of(
				Message.system(RUBRIC),
				Message.user("""
						[상황]
						%s

						[직전 손님의 말]
						%s

						[채점할 직원의 말]
						%s
						""".formatted(situation, context, userUtterance)));
	}

	/** 기동 시 캐시 예열용 — 루브릭만 */
	public List<Message> warmup() {
		return List.of(Message.system(RUBRIC));
	}

	private static Map<String, Object> schema() {
		Map<String, Object> axis = Map.of(
				"type", "object",
				"properties", Map.of(
						SCORE_KEY, Map.of("type", "integer", "minimum", AttitudeScores.MIN_SCORE, "maximum", AttitudeScores.MAX_SCORE),
						REASON_KEY, Map.of("type", "string", "minLength", 1, "maxLength", MAX_REASON_CHARS)),
				"required", List.of(SCORE_KEY, REASON_KEY),
				"additionalProperties", false);
		Map<String, Object> properties = new LinkedHashMap<>();
		Arrays.stream(AttitudeAxis.values()).forEach(a -> properties.put(a.schemaKey(), axis));
		return Map.of(
				"type", "object",
				"properties", properties,
				"required", Arrays.stream(AttitudeAxis.values()).map(AttitudeAxis::schemaKey).toList(),
				"additionalProperties", false);
	}
}
