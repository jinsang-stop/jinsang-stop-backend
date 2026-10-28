package com.jinsangstop.auth.service;

import com.jinsangstop.auth.domain.UserAccount;
import com.jinsangstop.auth.domain.UserAccountRepository;
import com.jinsangstop.auth.dto.SignupRequest;
import com.jinsangstop.common.error.BusinessException;
import com.jinsangstop.common.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입 서비스. 비밀번호는 BCrypt로 해시해서만 저장한다.
 */
@Service
public class AuthService {

	private final UserAccountRepository userAccountRepository;
	private final PasswordEncoder passwordEncoder;

	public AuthService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
		this.userAccountRepository = userAccountRepository;
		this.passwordEncoder = passwordEncoder;
	}

	/** 새 계정을 만든다. 아이디가 이미 있으면 DUPLICATE_LOGIN_ID. */
	@Transactional
	public UserAccount signup(SignupRequest request) {
		if (userAccountRepository.existsByLoginId(request.loginId())) {
			throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
		}
		UserAccount account = UserAccount.create(
				request.loginId(),
				passwordEncoder.encode(request.password()),
				request.displayName().strip());
		try {
			return userAccountRepository.saveAndFlush(account);
		} catch (DataIntegrityViolationException e) {
			// 동시에 같은 아이디로 가입한 경우 — 유니크 제약이 막는다
			throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
		}
	}
}
