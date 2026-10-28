package com.jinsangstop.scoring;

import com.jinsangstop.card.domain.CardType;
import com.jinsangstop.card.domain.ScenarioCardRepository;
import com.jinsangstop.inference.InferenceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 저장 발화 채점 테스트(PRD v2) — 재현성 → 축 독립성 → 직무 조치 비채점 순서로 실제 추론 서비스에 돌린다.
 * 실제 llama-server가 필요하므로 `JINSANGSTOP_RUN_INFERENCE_IT=1`일 때만 돈다.
 * 결과는 build/reports/scoring.md에 남긴다 — 모델·양자화를 바꿀 때마다 다시 돌린다.
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "JINSANGSTOP_RUN_INFERENCE_IT", matches = "1")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ScoringIntegrationTest {

	private static final Path REPORT = Path.of("build", "reports", "scoring.md");

	@Autowired
	private AttitudeScorer scorer;

	@Autowired
	private InferenceClient inferenceClient;

	@Autowired
	private ScenarioCardRepository scenarioCardRepository;

	private record Utterance(String kind, String previous, String text) {
	}

	@Test
	@Order(1)
	@DisplayName("재현성 — 같은 발화를 5회 채점하면 5회 모두 같은 점수")
	void reproducible() throws Exception {
		assertThat(inferenceClient.isReady()).as("추론 서비스가 떠 있어야 한다").isTrue();
		Files.createDirectories(REPORT.getParent());
		Files.writeString(REPORT, "## 채점 저장 발화 결과\n\n", StandardCharsets.UTF_8);

		Utterance u = byKind("REPRO");
		List<String> details = new ArrayList<>();
		List<String> scores = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			var result = scorer.score(situation(), u.previous(), u.text());
			details.add(result.map(ScoringIntegrationTest::brief).orElse("정보 없음"));
			scores.add(result.map(ScoringIntegrationTest::scoresOnly).orElse("정보 없음"));
		}
		append("### 1. 재현성 (5회)\n" + details.stream().map(r -> "- " + r).collect(Collectors.joining("\n")) + "\n\n");
		// 완료 조건은 "같은 점수"다 — 근거 문장은 프롬프트 캐시 여부에 따라 토큰 단위로 달라질 수 있어 비교하지 않는다
		assertThat(scores).doesNotContain("정보 없음");
		assertThat(scores.stream().distinct()).hasSize(1);
	}

	@Test
	@Order(2)
	@DisplayName("축 독립성 — 공감 없는 단호한 거절은 경계↑ 공감↓, 공감만 하고 수용하면 공감↑ 경계↓")
	void axesIndependent() throws Exception {
		AttitudeScores firm = scoreOf("FIRM");
		AttitudeScores soft = scoreOf("SOFT");
		append("### 2. 축 독립성\n- FIRM(단호한 거절): " + brief(firm) + "\n- SOFT(공감 + 수용): " + brief(soft) + "\n\n");
		assertThat(firm.boundarySetting().score()).as("FIRM 경계 설정").isGreaterThan(soft.boundarySetting().score());
		assertThat(soft.empathyAcknowledgement().score()).as("SOFT 공감 인정").isGreaterThan(firm.empathyAcknowledgement().score());
	}

	@Test
	@Order(3)
	@DisplayName("직무 조치 비채점 — 규정 안내 내용만 다른 두 발화는 세 축 점수가 같다")
	void jobActionNotScored() throws Exception {
		AttitudeScores a = scoreOf("JOB_A");
		AttitudeScores b = scoreOf("JOB_B");
		append("### 3. 직무 조치 비채점\n- JOB_A(맞는 규정): " + brief(a) + "\n- JOB_B(틀린 규정): " + brief(b) + "\n\n");
		for (AttitudeAxis axis : AttitudeAxis.values()) {
			assertThat(b.of(axis).score()).as(axis.displayName()).isEqualTo(a.of(axis).score());
		}
	}

	private AttitudeScores scoreOf(String kind) {
		Utterance u = byKind(kind);
		return scorer.score(situation(), u.previous(), u.text())
				.orElseThrow(() -> new AssertionError(kind + " 채점 실패(정보 없음)"));
	}

	private String situation() {
		return scenarioCardRepository.findAllByCardTypeOrderByIdAsc(CardType.OFFICIAL).getFirst().getSituation();
	}

	private static String scoresOnly(AttitudeScores s) {
		return s.emotionalStability().score() + "/" + s.empathyAcknowledgement().score() + "/" + s.boundarySetting().score();
	}

	private static String brief(AttitudeScores s) {
		return "정서 안정 %d · 공감 인정 %d · 경계 설정 %d — %s / %s / %s".formatted(
				s.emotionalStability().score(), s.empathyAcknowledgement().score(), s.boundarySetting().score(),
				s.emotionalStability().reason(), s.empathyAcknowledgement().reason(), s.boundarySetting().reason());
	}

	private static Utterance byKind(String kind) {
		try {
			Map<String, Utterance> all = Arrays.stream(new ClassPathResource("scoring/saved-utterances.txt")
							.getContentAsString(StandardCharsets.UTF_8).split("\\R"))
					.map(String::strip)
					.filter(line -> !line.isEmpty() && !line.startsWith("#"))
					.map(line -> line.split("\\|"))
					.map(parts -> new Utterance(parts[0].strip(), parts[1].strip(), parts[2].strip()))
					.collect(Collectors.toMap(Utterance::kind, u -> u));
			return all.get(kind);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static void append(String text) throws Exception {
		Files.writeString(REPORT, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	}
}
