package com.jinsangstop.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * 테스트용 시계 — 시각을 앞당겨 방치된 세션 같은 시간 의존 동작을 검증한다.
 */
public class MutableClock extends Clock {

	private Instant now;
	private final ZoneId zone;

	public MutableClock(Instant start, ZoneId zone) {
		this.now = start;
		this.zone = zone;
	}

	/** 시각을 앞으로 옮긴다. */
	public void advance(Duration duration) {
		now = now.plus(duration);
	}

	/** 시각을 지정한 값으로 되돌린다. */
	public void reset(Instant instant) {
		now = instant;
	}

	@Override
	public ZoneId getZone() {
		return zone;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return new MutableClock(now, zone);
	}

	@Override
	public Instant instant() {
		return now;
	}
}
