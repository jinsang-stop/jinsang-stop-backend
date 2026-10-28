package com.jinsangstop.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 사용자 계정. 훈련 세션과 성장 기록이 이 계정에 귀속된다(ADR-0006, ADR-0010).
 * 비밀번호는 해시로만 갖고 평문은 어디에도 남기지 않는다.
 */
@Entity
@Table(name = "user_account")
public class UserAccount {

	/** 사용자 식별자 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 로그인 아이디 */
	@Column(name = "login_id", nullable = false, unique = true, length = 20)
	private String loginId;

	/** BCrypt 비밀번호 해시 */
	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	/** 화면 표시 이름 */
	@Column(name = "display_name", nullable = false, length = 20)
	private String displayName;

	/** 가입 시각 */
	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private LocalDateTime createdAt;

	/** JPA 전용 기본 생성자 */
	protected UserAccount() {
	}

	private UserAccount(String loginId, String passwordHash, String displayName) {
		this.loginId = loginId;
		this.passwordHash = passwordHash;
		this.displayName = displayName;
	}

	/** 새 계정을 만든다. 비밀번호는 이미 해시된 값을 받는다. */
	public static UserAccount create(String loginId, String passwordHash, String displayName) {
		return new UserAccount(loginId, passwordHash, displayName);
	}

	public Long getId() {
		return id;
	}

	public String getLoginId() {
		return loginId;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getDisplayName() {
		return displayName;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
