package com.jinsangstop.voice;

/**
 * 음성 서비스(파이썬, faster-whisper · MeloTTS) 호출 계약(ADR-0008).
 * 브라우저는 음성 서비스를 직접 부르지 않고, 백엔드만 이 클라이언트로 부른다.
 */
public interface VoiceClient {

	/**
	 * 전사·합성이 모두 준비됐는지 확인한다. 연결 실패·시간 초과도 false다.
	 */
	boolean isReady();

	/**
	 * 오디오를 한국어로 전사한다. 말소리가 없으면 빈 문자열을 돌려준다(오류 아님).
	 * 오디오는 메모리에서만 다루고 디스크에 쓰지 않는다(ADR-0006).
	 *
	 * @param audio       오디오 바이트
	 * @param contentType 브라우저가 보낸 오디오 형식(예: audio/webm;codecs=opus)
	 * @throws VoiceServiceException 오디오를 읽지 못했거나(다시 말하면 풀림) 서비스 쪽 문제일 때
	 */
	String transcribe(byte[] audio, String contentType);

	/**
	 * 출력 검사를 통과한 민원인 대사를 한국어 음성(WAV)으로 합성한다. 합성 음성은 저장하지 않는다(ADR-0006).
	 *
	 * @throws VoiceServiceException 합성 실패(UNAVAILABLE)
	 */
	byte[] synthesize(String text);
}
