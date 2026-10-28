package com.jinsangstop.auth.security;

import com.jinsangstop.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 로그인하지 않은 채 보호된 API를 부르면 공통 오류 형식의 401(UNAUTHORIZED)을 돌려준다.
 * 시큐리티 필터 단계에서 끝나므로 컨트롤러의 예외 처리기를 거치지 않아 여기서 직접 본문을 쓴다.
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

	/** 고정 문자열만 들어가므로 JSON을 직접 조립한다. */
	private static final String BODY = "{\"status\":%d,\"code\":\"%s\",\"message\":\"%s\"}".formatted(
			ErrorCode.UNAUTHORIZED.status().value(),
			ErrorCode.UNAUTHORIZED.name(),
			ErrorCode.UNAUTHORIZED.message());

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		response.setStatus(ErrorCode.UNAUTHORIZED.status().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(BODY);
	}
}
