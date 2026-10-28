package com.jinsangstop.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 사용자 계정 저장소.
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

	/** 로그인 아이디로 계정을 찾는다. */
	Optional<UserAccount> findByLoginId(String loginId);

	/** 로그인 아이디가 이미 쓰이는지 확인한다. */
	boolean existsByLoginId(String loginId);
}
