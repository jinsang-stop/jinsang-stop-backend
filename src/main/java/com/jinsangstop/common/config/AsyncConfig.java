package com.jinsangstop.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 턴 처리 중 병렬로 돌리는 작업(연기와 동시에 하는 채점, 세션 시작 시 캐시 예열)의 실행기.
 * 추론 서비스는 슬롯 2개(연기·채점)뿐이므로 크게 잡지 않는다.
 */
@Configuration
public class AsyncConfig {

	/** 실행기 빈 이름 */
	public static final String TURN_EXECUTOR = "turnExecutor";

	@Bean(name = TURN_EXECUTOR)
	public ThreadPoolTaskExecutor turnExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(20);
		executor.setThreadNamePrefix("turn-");
		executor.initialize();
		return executor;
	}
}
