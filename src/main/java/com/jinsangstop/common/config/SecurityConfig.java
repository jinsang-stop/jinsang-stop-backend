package com.jinsangstop.common.config;

import com.jinsangstop.auth.security.JsonAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * 인증 설정 — 자체 아이디·비밀번호 + 서버 세션 쿠키(ADR-0010).
 * 공식 카드·강도 조회와 API 문서는 공개하고, 그 밖의 API는 로그인을 요구한다.
 */
@Configuration
public class SecurityConfig {

	/** 보안 필터 체인 */
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JsonAuthenticationEntryPoint entryPoint,
			SecurityContextRepository securityContextRepository) throws Exception {
		http
				// CORS는 CorsConfig(Spring MVC 설정)를 그대로 쓴다
				.cors(withDefaults())
				// SameSite=Lax 세션 쿠키 + 좁힌 CORS 출처로 CSRF를 대신 막는다(ADR-0010)
				.csrf(AbstractHttpConfigurer::disable)
				// 로그인·로그아웃은 AuthController가 JSON API로 처리한다
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.securityContext(context -> context.securityContextRepository(securityContextRepository))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/error").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login", "/api/auth/logout").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/cards", "/api/cards/*", "/api/intensities").permitAll()
						.anyRequest().authenticated())
				// 미인증 요청은 로그인 페이지로 보내지 않고 401 JSON으로 거절한다
				.exceptionHandling(exception -> exception.authenticationEntryPoint(entryPoint));
		return http.build();
	}

	/** 로그인 성공 시 인증 정보를 HttpSession에 저장하는 저장소 */
	@Bean
	public SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	/** 비밀번호 해시 — BCrypt */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/** 아이디·비밀번호 인증기 — AuthController의 로그인에서 쓴다 */
	@Bean
	public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}
}
