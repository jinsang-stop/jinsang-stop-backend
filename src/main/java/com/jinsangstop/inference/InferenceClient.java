package com.jinsangstop.inference;

import java.util.List;
import java.util.Map;

/**
 * 추론 서비스(llama.cpp llama-server, OpenAI 호환) 호출 계약(ADR-0009).
 * 추론 서비스는 프롬프트를 조립하지 않고 카드·루브릭을 모른다 — 조립은 호출하는 쪽(연기·채점 모듈)이 한다.
 */
public interface InferenceClient {

	/** 모델이 올라와 요청을 받을 수 있는지. 연결 실패·시간 초과도 false다. */
	boolean isReady();

	/**
	 * 채팅 완성을 요청하고 응답 본문(content) 문자열을 돌려준다.
	 *
	 * @throws InferenceServiceException 연결 실패·시간 초과·오류 응답
	 */
	String complete(ChatRequest request);

	/** 대화 메시지 한 건 */
	record Message(String role, String content) {

		public static Message system(String content) {
			return new Message("system", content);
		}

		public static Message user(String content) {
			return new Message("user", content);
		}

		public static Message assistant(String content) {
			return new Message("assistant", content);
		}
	}

	/**
	 * 요청 — 슬롯(연기 0 / 채점 1)·temperature·시드·응답 JSON 스키마.
	 * 추론 모드(thinking)는 클라이언트가 언제나 끈다.
	 *
	 * @param schemaName JSON 스키마 이름. null이면 자유 형식
	 * @param schema     JSON 스키마. null이면 자유 형식
	 */
	record ChatRequest(List<Message> messages, int slot, double temperature, long seed, int maxTokens,
			String schemaName, Map<String, Object> schema) {
	}
}
