package com.jinsangstop.voice;

/**
 * 음성 서비스 호출 실패. "누구 탓인가"로 종류를 나눈다 — 사용자가 다시 말하면 풀리는지, 환경 문제인지.
 */
public class VoiceServiceException extends RuntimeException {

	/** 실패 종류 */
	public enum Kind {
		/** 보낸 오디오를 읽지 못함(음성 서비스 422) — 다시 녹음하면 풀린다 */
		AUDIO_UNREADABLE,
		/** 오디오가 음성 서비스 상한을 넘음(음성 서비스 413) */
		AUDIO_TOO_LARGE,
		/** 연결 실패·시간 초과·모델 미준비·전사 실패(음성 서비스 503 등) — 다시 말해도 풀리지 않는다 */
		UNAVAILABLE
	}

	private final Kind kind;

	public VoiceServiceException(Kind kind, String message) {
		super(message);
		this.kind = kind;
	}

	public VoiceServiceException(Kind kind, String message, Throwable cause) {
		super(message, cause);
		this.kind = kind;
	}

	public Kind getKind() {
		return kind;
	}
}
