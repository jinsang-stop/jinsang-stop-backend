package com.jinsangstop.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;

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

		@Schema(description = "비밀번호 — 8~64자, UTF-8 72바이트 이내(한글은 24자까지)", example = "password1234", format = "password",
				requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "비밀번호를 입력해 주세요.")
		@Size(min = 8, max = 64, message = "비밀번호는 8~64자입니다.")
		String password,

		@Schema(description = "화면에 보일 이름 — 1~20자", example = "김알바", requiredMode = Schema.RequiredMode.REQUIRED)
		@NotBlank(message = "이름을 입력해 주세요.")
		@Size(max = 20, message = "이름은 20자 이하입니다.")
		String displayName
) {

	/** BCrypt가 받을 수 있는 비밀번호 최대 바이트 — 넘으면 해시 단계에서 예외가 나 500이 된다 */
	private static final int BCRYPT_MAX_BYTES = 72;

	/**
	 * 비밀번호가 BCrypt 한도(72바이트) 안인지. 글자 수 검사만으로는 한글(글자당 3바이트) 25자 이상을 막지 못한다.
	 */
	@JsonIgnore
	@Schema(hidden = true)
	@AssertTrue(message = "비밀번호가 너무 깁니다. 한글은 24자, 영문·숫자는 64자 이내로 입력해 주세요.")
	public boolean isPasswordWithinBcryptLimit() {
		return password == null || password.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES;
	}
}
