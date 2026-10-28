package com.jinsangstop.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 현재 시각을 한곳에서 주입한다. 테스트에서 시각을 고정하거나 앞당길 수 있게 하기 위함이다.
 */
@Configuration
public class ClockConfig {

	/** 시스템 기본 시간대의 시계 */
	@Bean
	public Clock clock() {
		return Clock.systemDefaultZone();
	}
}
