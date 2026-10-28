package com.jinsangstop.auth.security;

import com.jinsangstop.auth.domain.UserAccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 시 아이디로 계정을 불러오는 Spring Security 연결 지점.
 */
@Service
@Transactional(readOnly = true)
public class LoginUserDetailsService implements UserDetailsService {

	private final UserAccountRepository userAccountRepository;

	public LoginUserDetailsService(UserAccountRepository userAccountRepository) {
		this.userAccountRepository = userAccountRepository;
	}

	/** 아이디로 계정을 찾는다. 없으면 예외 — 응답에서는 비밀번호 불일치와 구분되지 않는다. */
	@Override
	public UserDetails loadUserByUsername(String loginId) {
		return userAccountRepository.findByLoginId(loginId)
				.map(LoginUser::from)
				.orElseThrow(() -> new UsernameNotFoundException("계정 없음"));
	}
}
