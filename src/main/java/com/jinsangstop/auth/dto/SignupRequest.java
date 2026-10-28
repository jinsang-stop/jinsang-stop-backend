package com.jinsangstop.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청. 검증 메시지는 오류 응답의 message로 그대로 사용자에게 보인다.
 */
@Schema(description = "회원가입 요청")
public record SignupRequest(
		@Schema(description = "로그인 아이디 — 영문 소문자·숫자·밑줄(_) 4~20자", example = "trainee01",
				requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "아이디를 입력해 주세요.")
		@Pattern(regexp = "^[a-z0-9_]{4,20}$", message = "아이디는 영문 소문자·숫자·밑줄(_) 4~20자입니다.")
		String loginId,

		@Schema(description = "비밀번호 — 8~64자", example = "password1234", format = "password",
				requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "비밀번호를 입력해 주세요.")
		@Size(min = 8, max = 64, message = "비밀번호는 8~64자입니다.")
		String password,

		@Schema(description = "화면에 보일 이름 — 1~20자", example = "김알바", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "이름을 입력해 주세요.")
		@Size(max = 20, message = "이름은 20자 이하입니다.")
		String displayName
) {
}
