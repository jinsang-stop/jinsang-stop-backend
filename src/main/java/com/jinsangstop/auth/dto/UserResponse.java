package com.jinsangstop.auth.dto;

import com.jinsangstop.auth.domain.UserAccount;
import com.jinsangstop.auth.security.LoginUser;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 프론트엔드에 내려가는 사용자 정보. 비밀번호 관련 값은 담지 않는다.
 */
@Schema(description = "사용자 정보")
public record UserResponse(
		@Schema(description = "사용자 식별자", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
		Long userId,
		@Schema(description = "로그인 아이디", example = "trainee01", requiredMode = Schema.RequiredMode.REQUIRED)
		String loginId,
		@Schema(description = "화면에 보일 이름 — 로그인 후 상단 등에 표시", example = "김알바",
				requiredMode = Schema.RequiredMode.REQUIRED)
		String displayName
) {

	/** 계정 엔티티에서 만든다. */
	public static UserResponse from(UserAccount account) {
		return new UserResponse(account.getId(), account.getLoginId(), account.getDisplayName());
	}

	/** 세션의 로그인 사용자에서 만든다. */
	public static UserResponse from(LoginUser loginUser) {
		return new UserResponse(loginUser.getUserId(), loginUser.getLoginId(), loginUser.getDisplayName());
	}
}
