package com.jinsangstop.auth;

import com.jinsangstop.auth.domain.UserAccount;
import com.jinsangstop.auth.domain.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 회원가입·로그인·로그아웃·현재 사용자 API와 미인증 거절을 검증한다(ADR-0010).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiTest {

	private static final String SIGNUP_BODY = """
			{ "loginId": "trainee01", "password": "password1234", "displayName": "김알바" }""";
	private static final String LOGIN_BODY = """
			{ "loginId": "trainee01", "password": "password1234" }""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountRepository userAccountRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	/** 테스트끼리 계정이 섞이지 않게 매번 비운다. */
	@BeforeEach
	void clearUsers() {
		userAccountRepository.deleteAll();
	}

	@Test
	@DisplayName("가입하면 201과 함께 바로 로그인되어 /me가 200이다")
	void signupLogsIn() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON).content(SIGNUP_BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.userId").isNumber())
				.andExpect(jsonPath("$.loginId").value("trainee01"))
				.andExpect(jsonPath("$.displayName").value("김알바"))
				.andExpect(jsonPath("$.password").doesNotExist())
				.andReturn();

		MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.loginId").value("trainee01"));
	}

	@Test
	@DisplayName("DB에는 평문 비밀번호가 없고 BCrypt 해시만 있다")
	void passwordIsHashed() throws Exception {
		signup();

		UserAccount account = userAccountRepository.findByLoginId("trainee01").orElseThrow();
		assertThat(account.getPasswordHash()).isNotEqualTo("password1234").startsWith("$2");

		// 테이블 전체 값 어디에도 평문이 없는지 확인
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM user_account");
		assertThat(rows).allSatisfy(row -> assertThat(row.values())
				.noneMatch(value -> String.valueOf(value).contains("password1234")));
	}

	@Test
	@DisplayName("이미 있는 아이디로 가입하면 409 DUPLICATE_LOGIN_ID")
	void duplicateLoginId() throws Exception {
		signup();

		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(SIGNUP_BODY))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_LOGIN_ID"));
	}

	@Test
	@DisplayName("형식에 맞지 않는 아이디는 400과 함께 무엇이 틀렸는지 알려준다")
	void invalidSignup() throws Exception {
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "loginId": "AB", "password": "password1234", "displayName": "김알바" }"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.message").value("아이디는 영문 소문자·숫자·밑줄(_) 4~20자입니다."));
	}

	@Test
	@DisplayName("깨진 JSON 본문은 400 INVALID_REQUEST")
	void malformedJson() throws Exception {
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("로그인하면 HttpOnly 세션 쿠키가 내려가고 /me가 200이다")
	void loginSetsSession() throws Exception {
		signup();

		MvcResult result = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.loginId").value("trainee01"))
				.andReturn();

		MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
		assertThat(session).isNotNull();
		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("김알바"));
	}

	@Test
	@DisplayName("비밀번호가 틀리거나 없는 아이디면 같은 401 LOGIN_FAILED")
	void loginFailed() throws Exception {
		signup();

		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "loginId": "trainee01", "password": "wrong-password" }"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
		mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
						.content("""
								{ "loginId": "nobody", "password": "password1234" }"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
				.andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 올바르지 않습니다."));
	}

	@Test
	@DisplayName("로그인 없이 보호된 API를 부르면 공통 오류 형식의 401 UNAUTHORIZED")
	void unauthenticatedRejected() throws Exception {
		mockMvc.perform(get("/api/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("Content-Type", containsString("application/json")))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
	}

	@Test
	@DisplayName("로그아웃하면 204이고 이후 같은 세션으로 /me가 401이다")
	void logout() throws Exception {
		MockHttpSession session = signup();

		mockMvc.perform(post("/api/auth/logout").session(session))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/auth/me").session(session))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("로그인하지 않은 상태의 로그아웃도 204다")
	void logoutWithoutLogin() throws Exception {
		mockMvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("프론트 출처의 로그인 사전 요청(preflight)이 쿠키 포함으로 허용된다")
	void loginPreflightAllowsCredentials() throws Exception {
		mockMvc.perform(options("/api/auth/login")
						.header("Origin", "http://localhost:5173")
						.header("Access-Control-Request-Method", "POST")
						.header("Access-Control-Request-Headers", "content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
				.andExpect(header().string("Access-Control-Allow-Credentials", "true"));
	}

	/** 기본 계정으로 가입하고 그 세션을 돌려준다. */
	private MockHttpSession signup() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON).content(SIGNUP_BODY))
				.andExpect(status().isCreated())
				.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}
}
