package com.jinsangstop.auth.controller;

import com.jinsangstop.auth.domain.UserAccount;
import com.jinsangstop.auth.dto.LoginRequest;
import com.jinsangstop.auth.dto.SignupRequest;
import com.jinsangstop.auth.dto.UserResponse;
import com.jinsangstop.auth.security.LoginUser;
import com.jinsangstop.auth.service.AuthService;
import com.jinsangstop.common.config.OpenApiConfig;
import com.jinsangstop.common.error.BusinessException;
import com.jinsangstop.common.error.ErrorCode;
import com.jinsangstop.common.error.ErrorExamples;
import com.jinsangstop.common.error.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원가입·로그인·로그아웃·현재 사용자 조회 API(ADR-0010).
 * 로그인에 성공하면 서버 세션이 만들어지고 응답의 `Set-Cookie: JSESSIONID`로 세션 쿠키가 내려간다.
 */
@Tag(name = OpenApiConfig.TAG_AUTH)
@RestController
@RequestMapping(value = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {

	/** 세션 쿠키 이름(서블릿 컨테이너 기본값) */
	private static final String SESSION_COOKIE = "JSESSIONID";

	/** Swagger 예시 — 사용자 정보 */
	private static final String USER_EXAMPLE = """
			{ "userId": 1, "loginId": "trainee01", "displayName": "김알바" }""";

	private final AuthService authService;
	private final AuthenticationManager authenticationManager;
	private final SecurityContextRepository securityContextRepository;
	private final SecurityContextHolderStrategy securityContextHolderStrategy =
			SecurityContextHolder.getContextHolderStrategy();

	public AuthController(AuthService authService, AuthenticationManager authenticationManager,
			SecurityContextRepository securityContextRepository) {
		this.authService = authService;
		this.authenticationManager = authenticationManager;
		this.securityContextRepository = securityContextRepository;
	}

	/** 회원가입 — 가입과 동시에 로그인된다 */
	@Operation(summary = "회원가입",
			description = """
					새 계정을 만들고 **바로 로그인 상태로 만든다**(응답에 세션 쿠키가 실린다).
					가입 후 로그인 API를 따로 부를 필요가 없다.
					검증 실패 시 `message`에 어떤 값이 잘못됐는지가 담겨 그대로 보여 주면 된다.""")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "가입·로그인 성공",
					content = @Content(schema = @Schema(implementation = UserResponse.class),
							examples = @ExampleObject(name = "가입한 사용자", value = USER_EXAMPLE))),
			@ApiResponse(responseCode = "400", description = "입력값 검증 실패(INVALID_REQUEST)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "INVALID_REQUEST", value = ErrorExamples.INVALID_SIGNUP))),
			@ApiResponse(responseCode = "409", description = "이미 쓰는 아이디(DUPLICATE_LOGIN_ID)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "DUPLICATE_LOGIN_ID", value = ErrorExamples.DUPLICATE_LOGIN_ID)))
	})
	@PostMapping(value = "/signup", consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse signup(@Valid @RequestBody SignupRequest request,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		UserAccount account = authService.signup(request);
		authenticate(request.loginId(), request.password(), httpRequest, httpResponse);
		return UserResponse.from(account);
	}

	/** 로그인 */
	@Operation(summary = "로그인",
			description = """
					아이디·비밀번호가 맞으면 세션을 만들고 `Set-Cookie: JSESSIONID`(HttpOnly)를 내려준다.
					이후 요청은 브라우저가 쿠키를 자동으로 보낸다 — 프론트는 `fetch`에 `credentials: 'include'`(axios는 `withCredentials: true`)만 켜면 된다.
					아이디가 없는 경우와 비밀번호가 틀린 경우를 구분하지 않고 같은 `LOGIN_FAILED`를 준다.""")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "로그인 성공",
					content = @Content(schema = @Schema(implementation = UserResponse.class),
							examples = @ExampleObject(name = "로그인한 사용자", value = USER_EXAMPLE))),
			@ApiResponse(responseCode = "400", description = "아이디·비밀번호 누락(INVALID_REQUEST)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "INVALID_REQUEST", value = ErrorExamples.INVALID_REQUEST))),
			@ApiResponse(responseCode = "401", description = "아이디 또는 비밀번호 불일치(LOGIN_FAILED)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "LOGIN_FAILED", value = ErrorExamples.LOGIN_FAILED)))
	})
	@PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
	public UserResponse login(@Valid @RequestBody LoginRequest request,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		LoginUser loginUser = authenticate(request.loginId(), request.password(), httpRequest, httpResponse);
		return UserResponse.from(loginUser);
	}

	/** 로그아웃 */
	@Operation(summary = "로그아웃",
			description = "세션을 끝내고 세션 쿠키를 지운다. 로그인하지 않은 상태에서 불러도 204다(여러 번 불러도 안전).")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "로그아웃 완료(본문 없음)")
	})
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(HttpServletRequest request, HttpServletResponse response) {
		Authentication authentication = securityContextHolderStrategy.getContext().getAuthentication();
		new SecurityContextLogoutHandler().logout(request, response, authentication);
		new CookieClearingLogoutHandler(SESSION_COOKIE).logout(request, response, authentication);
	}

	/** 현재 사용자 */
	@Operation(summary = "현재 사용자 조회",
			description = """
					세션 쿠키로 로그인한 사용자를 돌려준다.
					앱 시작·새로고침 때 불러 로그인 상태를 복원하는 용도다. 401이면 로그인 화면으로 보낸다.""",
			security = @SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE_SCHEME))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "로그인 상태",
					content = @Content(schema = @Schema(implementation = UserResponse.class),
							examples = @ExampleObject(name = "현재 사용자", value = USER_EXAMPLE))),
			@ApiResponse(responseCode = "401", description = "로그인하지 않음(UNAUTHORIZED)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "UNAUTHORIZED", value = ErrorExamples.UNAUTHORIZED)))
	})
	@GetMapping("/me")
	public UserResponse me(@AuthenticationPrincipal LoginUser loginUser) {
		return UserResponse.from(loginUser);
	}

	/**
	 * 아이디·비밀번호로 인증하고 결과를 세션에 저장한다.
	 * 세션 고정 공격을 막기 위해 기존 세션이 있으면 세션 ID를 새로 발급한다.
	 */
	private LoginUser authenticate(String loginId, String password,
			HttpServletRequest request, HttpServletResponse response) {
		Authentication authentication;
		try {
			authentication = authenticationManager.authenticate(
					UsernamePasswordAuthenticationToken.unauthenticated(loginId, password));
		} catch (AuthenticationException e) {
			throw new BusinessException(ErrorCode.LOGIN_FAILED);
		}

		if (request.getSession(false) != null) {
			request.changeSessionId();
		}
		SecurityContext context = securityContextHolderStrategy.createEmptyContext();
		context.setAuthentication(authentication);
		securityContextHolderStrategy.setContext(context);
		securityContextRepository.saveContext(context, request, response);
		return (LoginUser) authentication.getPrincipal();
	}
}
