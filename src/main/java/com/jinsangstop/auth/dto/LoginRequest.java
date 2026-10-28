package com.jinsangstop.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청.
 */
@Schema(description = "로그인 요청")
public record LoginRequest(
		@Schema(description = "로그인 아이디", example = "trainee01", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "아이디를 입력해 주세요.")
		String loginId,

		@Schema(description = "비밀번호", example = "password1234", format = "password",
				requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "비밀번호를 입력해 주세요.")
		String password
) {
}
