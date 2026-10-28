package com.jinsangstop.complainant.safety;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 절대선 문서(`safety/absolute-lines.txt`)를 금칙 목록으로 읽는다.
 * 목록은 코드가 아니라 문서로 관리한다(ADR-0005) — 표현을 바꾸려면 문서만 고친다.
 */
@Component
public class AbsoluteLineList {

	/** 금칙 표현 하나 — 분류와 정규화된 표현 */
	public record ForbiddenTerm(String category, String normalized) {
	}

	private final List<ForbiddenTerm> terms;

	public AbsoluteLineList(@Value("${jinsangstop.safety.absolute-lines}") Resource document) {
		this.terms = List.copyOf(parse(document));
	}

	/** 대사에 들어 있는 첫 금칙 표현을 찾는다. 없으면 빈 값. */
	public Optional<ForbiddenTerm> findViolation(String line) {
		String normalized = normalize(line);
		return terms.stream().filter(term -> normalized.contains(term.normalized())).findFirst();
	}

	/** 읽어 들인 금칙 표현 수 */
	public int size() {
		return terms.size();
	}

	/** 띄어쓰기를 모두 지우고 소문자로 — 띄어쓰기 우회를 막는다 */
	static String normalize(String text) {
		return text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
	}

	/** 문서 형식: `#` 주석, `[분류]` 제목, 나머지 줄은 금칙 표현 */
	private static List<ForbiddenTerm> parse(Resource document) {
		List<ForbiddenTerm> parsed = new ArrayList<>();
		String category = "미분류";
		try {
			for (String raw : document.getContentAsString(StandardCharsets.UTF_8).split("\\R")) {
				String line = raw.strip();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				if (line.startsWith("[") && line.endsWith("]")) {
					category = line.substring(1, line.length() - 1).strip();
					continue;
				}
				parsed.add(new ForbiddenTerm(category, normalize(line)));
			}
		} catch (IOException e) {
			throw new UncheckedIOException("절대선 문서를 읽지 못했다: " + document, e);
		}
		if (parsed.isEmpty()) {
			// 금칙 목록이 비면 출력 검사가 사실상 꺼진다 — 조용히 넘어가지 않고 기동을 멈춘다
			throw new IllegalStateException("절대선 문서에 금칙 표현이 하나도 없다: " + document);
		}
		return parsed;
	}
}
