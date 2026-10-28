package com.jinsangstop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 「진상 멈춰」 백엔드 진입점.
 * 훈련 세션 오케스트레이션, 시나리오 카드 저장소, 성장 집계를 담당하는 Spring 애플리케이션을 띄운다.
 */
@SpringBootApplication
public class JinsangStopBackendApplication {

	/** 애플리케이션을 시작한다. */
	public static void main(String[] args) {
		SpringApplication.run(JinsangStopBackendApplication.class, args);
	}

}
