package com.jinsangstop.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 프론트엔드 개발 서버가 `/api/**`를 호출할 수 있도록 CORS를 연다.
 * 허용 출처는 환경변수 `CORS_ALLOWED_ORIGINS`(쉼표 구분)로 주입한다.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

	/** 허용할 프론트엔드 출처 목록 */
	private final String[] allowedOrigins;

	public CorsConfig(@Value("${jinsangstop.cors.allowed-origins}") String[] allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	/** API 경로에만 CORS 규칙을 등록한다. */
	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOrigins(allowedOrigins)
				.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
				.allowedHeaders("*")
				.allowCredentials(true);
	}
}
