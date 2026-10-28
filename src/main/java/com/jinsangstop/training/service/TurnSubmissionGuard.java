package com.jinsangstop.training.service;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 같은 세션에 발화 턴이 동시에 두 번 들어오는 것을 막는다.
 * "말하기 끝내기"를 두 번 누르면 같은 녹음으로 턴이 두 개 확정돼 하드 턴 상한과 소요 턴 수가 틀어진다.
 * 서버는 시연 장비 한 대에서만 돈다(ADR-0009)는 전제라 메모리 집합으로 충분하다.
 */
@Component
public class TurnSubmissionGuard {

	/** 지금 턴을 처리 중인 세션 */
	private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

	/** 처리를 시작한다. 이미 처리 중이면 false */
	public boolean tryAcquire(Long sessionId) {
		return inFlight.add(sessionId);
	}

	/** 처리를 끝낸다(성공·실패 모두). */
	public void release(Long sessionId) {
		inFlight.remove(sessionId);
	}
}
