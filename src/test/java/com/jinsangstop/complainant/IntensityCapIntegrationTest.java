package com.jinsangstop.complainant;

import com.jinsangstop.card.domain.CardType;
import com.jinsangstop.card.domain.ScenarioCard;
import com.jinsangstop.card.domain.ScenarioCardRepository;
import com.jinsangstop.complainant.safety.AbsoluteLineList;
import com.jinsangstop.complainant.safety.OutputChecker;
import com.jinsangstop.inference.InferenceClient;
import com.jinsangstop.inference.InferenceClient.ChatRequest;
import com.jinsangstop.intensity.domain.Intensity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 강도 상한 테스트(PRD v2) — 저장한 도발 입력 묶음을 강도 1~3 프롬프트로 실제 추론 서비스에 보내,
 * **출력 검사 전 원본 생성물**에 절대선 표현이 나오는지 본다.
 * 실제 llama-server가 필요하므로 `JINSANGSTOP_RUN_INFERENCE_IT=1`일 때만 돈다(주소는 INFERENCE_SERVICE_URL).
 * 결과 표는 build/reports/intensity-cap.md에 남긴다 — 모델·양자화를 바꿀 때마다 다시 돌려 이슈에 남긴다.
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "JINSANGSTOP_RUN_INFERENCE_IT", matches = "1")
class IntensityCapIntegrationTest {

	@Autowired
	private InferenceClient inferenceClient;

	@Autowired
	private ComplainantPromptAssembler promptAssembler;

	@Autowired
	private AbsoluteLineList absoluteLineList;

	@Autowired
	private OutputChecker outputChecker;

	@Autowired
	private ScenarioCardRepository scenarioCardRepository;

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	@DisplayName("강도 1~3 모두 저장 도발 입력에 절대선 표현을 생성하지 않는다")
	void noAbsoluteLineAtAnyIntensity() throws Exception {
		assertThat(inferenceClient.isReady()).as("추론 서비스가 떠 있어야 한다").isTrue();
		ScenarioCard card = scenarioCardRepository.findAllByCardTypeOrderByIdAsc(CardType.OFFICIAL).getFirst();
		List<String> provocations = loadProvocations();

		StringBuilder report = new StringBuilder("| 강도 | 도발 입력 | 원본 대사 | 절대선 | 출력 검사 |\n|---|---|---|---|---|\n");
		List<String> violations = new ArrayList<>();
		for (Intensity intensity : Intensity.values()) {
			for (int i = 0; i < provocations.size(); i++) {
				String provocation = provocations.get(i);
				String content = inferenceClient.complete(new ChatRequest(
						promptAssembler.assemble(card, intensity, List.of(), provocation),
						ComplainantActor.ACTING_SLOT, 0.8, 1000L + intensity.level() * 100 + i, 200,
						ComplainantPromptAssembler.SCHEMA_NAME, ComplainantPromptAssembler.SCHEMA));
				String line = extractLine(content);
				var violation = absoluteLineList.findViolation(line);
				OutputChecker.Result check = outputChecker.check(line);
				violation.ifPresent(v -> violations.add(intensity.level() + "단계: " + v.category()));
				report.append("| %d | %s | %s | %s | %s |\n".formatted(intensity.level(), provocation,
						violation.isPresent() ? "(가림)" : line.replace("|", "／"),
						violation.map(AbsoluteLineList.ForbiddenTerm::category).orElse("없음"),
						check.passed() ? "통과" : "실패: " + check.reason()));
			}
		}

		Path out = Path.of("build", "reports", "intensity-cap.md");
		Files.createDirectories(out.getParent());
		Files.writeString(out, report.toString(), StandardCharsets.UTF_8);
		assertThat(violations).as("원본 생성물의 절대선 표현").isEmpty();
	}

	private String extractLine(String content) {
		try {
			JsonNode node = jsonMapper.readTree(content);
			JsonNode line = node.get(ComplainantPromptAssembler.LINE_KEY);
			return line == null ? content : line.asString();
		} catch (RuntimeException e) {
			return content;
		}
	}

	private static List<String> loadProvocations() throws Exception {
		return Arrays.stream(new ClassPathResource("provocations/intensity-cap.txt")
						.getContentAsString(StandardCharsets.UTF_8).split("\\R"))
				.map(String::strip)
				.filter(line -> !line.isEmpty() && !line.startsWith("#"))
				.toList();
	}
}
