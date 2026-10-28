package com.jinsangstop.scoring;

import com.jinsangstop.inference.InferenceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 채점 응답 검증 테스트 — 범위·필수 값을 어기면 정보 없음(빈 값)이다. 0점으로 대신하지 않는다.
 */
class AttitudeScorerTest {

	private static final String VALID = """
			{"정서안정": {"점수": 4, "근거": "차분했다."}, "공감인정": {"점수": 1, "근거": "감정을 무시했다."}, "경계설정": {"점수": 5, "근거": "분명히 거절했다."}}""";

	private final AtomicReference<String> reply = new AtomicReference<>(VALID);
	private final AtomicReference<InferenceClient.ChatRequest> lastRequest = new AtomicReference<>();
	private final InferenceClient fake = new InferenceClient() {
		@Override
		public boolean isReady() {
			return true;
		}

		@Override
		public String complete(ChatRequest request) {
			lastRequest.set(request);
			if (reply.get() == null) {
				throw new RuntimeException("down");
			}
			return reply.get();
		}
	};
	private final AttitudeScorer scorer = new AttitudeScorer(fake, new ScoringPromptAssembler(),
			JsonMapper.builder().build(), 42, 300);

	@Test
	@DisplayName("세 축이 모두 유효하면 점수와 근거를 돌려준다")
	void valid() {
		AttitudeScores scores = scorer.score("상황", null, "안 됩니다").orElseThrow();
		assertThat(scores.emotionalStability().score()).isEqualTo(4);
		assertThat(scores.empathyAcknowledgement().score()).isEqualTo(1);
		assertThat(scores.boundarySetting()).isEqualTo(new AttitudeScores.AxisScore(5, "분명히 거절했다."));
	}

	@Test
	@DisplayName("호출 조건 — 슬롯 1, temperature 0, 고정 시드, 스키마")
	void requestShape() {
		scorer.score("상황", "환불해 줘요", "안 됩니다");
		InferenceClient.ChatRequest request = lastRequest.get();
		assertThat(request.slot()).isEqualTo(1);
		assertThat(request.temperature()).isZero();
		assertThat(request.seed()).isEqualTo(42);
		assertThat(request.schemaName()).isEqualTo("attitude_score");
		assertThat(request.messages().getFirst().content()).contains("직무 조치의 옳고 그름은 채점하지 않는다", "세 축은 서로 독립");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			// 범위 밖
			"{\"정서안정\": {\"점수\": 0, \"근거\": \"a\"}, \"공감인정\": {\"점수\": 3, \"근거\": \"a\"}, \"경계설정\": {\"점수\": 3, \"근거\": \"a\"}}",
			"{\"정서안정\": {\"점수\": 6, \"근거\": \"a\"}, \"공감인정\": {\"점수\": 3, \"근거\": \"a\"}, \"경계설정\": {\"점수\": 3, \"근거\": \"a\"}}",
			// 정수가 아님
			"{\"정서안정\": {\"점수\": 3.5, \"근거\": \"a\"}, \"공감인정\": {\"점수\": 3, \"근거\": \"a\"}, \"경계설정\": {\"점수\": 3, \"근거\": \"a\"}}",
			// 축 누락
			"{\"정서안정\": {\"점수\": 3, \"근거\": \"a\"}, \"공감인정\": {\"점수\": 3, \"근거\": \"a\"}}",
			// 빈 근거
			"{\"정서안정\": {\"점수\": 3, \"근거\": \" \"}, \"공감인정\": {\"점수\": 3, \"근거\": \"a\"}, \"경계설정\": {\"점수\": 3, \"근거\": \"a\"}}",
			// JSON 아님
			"점수: 3점"
	})
	@DisplayName("범위·형식을 어기면 정보 없음")
	void invalidIsUnknown(String content) {
		reply.set(content);
		assertThat(scorer.score("상황", null, "안 됩니다")).isEmpty();
	}

	@Test
	@DisplayName("호출 실패도 정보 없음 — 예외를 던지지 않는다")
	void callFailureIsUnknown() {
		reply.set(null);
		assertThat(scorer.score("상황", null, "안 됩니다")).isEmpty();
	}

	@Test
	@DisplayName("80자를 넘는 근거는 잘라 저장한다")
	void longReasonTruncated() {
		reply.set(VALID.replace("차분했다.", "가".repeat(120)));
		assertThat(scorer.score("상황", null, "안 됩니다").orElseThrow().emotionalStability().reason()).hasSize(80);
	}
}
