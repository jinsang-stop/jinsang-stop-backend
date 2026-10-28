package com.jinsangstop.auth.security;

import com.jinsangstop.auth.domain.UserAccount;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 세션에 저장되는 로그인 사용자 정보.
 * 컨트롤러에서는 `@AuthenticationPrincipal LoginUser`로 현재 사용자를 받는다.
 * 인증이 끝나면 비밀번호 해시를 지워(eraseCredentials) 세션에 남기지 않는다.
 */
public class LoginUser implements UserDetails, CredentialsContainer {

	/** 사용자 식별자 — 훈련 세션 등을 계정에 귀속시킬 때 쓴다 */
	private final Long userId;
	/** 로그인 아이디 */
	private final String loginId;
	/** 화면 표시 이름 */
	private final String displayName;
	/** 비밀번호 해시 — 인증 직후 지워진다 */
	private String passwordHash;

	private LoginUser(Long userId, String loginId, String displayName, String passwordHash) {
		this.userId = userId;
		this.loginId = loginId;
		this.displayName = displayName;
		this.passwordHash = passwordHash;
	}

	/** 계정 엔티티에서 만든다. */
	public static LoginUser from(UserAccount account) {
		return new LoginUser(account.getId(), account.getLoginId(), account.getDisplayName(), account.getPasswordHash());
	}

	public Long getUserId() {
		return userId;
	}

	public String getLoginId() {
		return loginId;
	}

	public String getDisplayName() {
		return displayName;
	}

	/** 모든 사용자는 같은 권한 하나만 갖는다(관리자 권한은 범위 밖). */
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_USER"));
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return loginId;
	}

	/** 인증이 끝난 뒤 비밀번호 해시를 지운다. */
	@Override
	public void eraseCredentials() {
		this.passwordHash = null;
	}
}
