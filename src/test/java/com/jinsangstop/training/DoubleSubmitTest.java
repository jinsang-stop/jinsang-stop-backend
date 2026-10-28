package com.jinsangstop.training;

import com.jinsangstop.inference.InferenceClient;
import com.jinsangstop.support.TestDataCleaner;
import com.jinsangstop.voice.VoiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 중복 제출 테스트 — "말하기 끝내기"를 두 번 눌러 같은 세션에 턴이 동시에 들어와도 턴은 하나만 확정된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DoubleSubmitTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoBean
	private VoiceClient voiceClient;

	@MockitoBean
	private InferenceClient inferenceClient;

	@Test
	@DisplayName("처리 중인 세션에 두 번째 턴이 오면 409 TURN_ALREADY_IN_PROGRESS이고 턴은 하나만 저장된다")
	void secondConcurrentSubmitRejected() throws Exception {
		TestDataCleaner.clearUserData(jdbcTemplate);
		when(voiceClient.isReady()).thenReturn(true);
		when(inferenceClient.isReady()).thenReturn(true);
		when(inferenceClient.complete(any())).thenReturn("{\"대사\": \"환불해 줘요\", \"진정함\": false}");
		when(voiceClient.synthesize(anyString())).thenReturn("RIFF".getBytes());
		CountDownLatch firstStarted = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		when(voiceClient.transcribe(any(), anyString())).thenAnswer(invocation -> {
			firstStarted.countDown();
			release.await(10, TimeUnit.SECONDS);
			return "죄송합니다 손님";
		});
		MockHttpSession user = signupAndStart();
		long sessionId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM training_session", Long.class);

		CompletableFuture<MvcResult> first = CompletableFuture.supplyAsync(() -> submit(user, sessionId));
		assertThat(firstStarted.await(10, TimeUnit.SECONDS)).isTrue();
		MvcResult second = submit(user, sessionId);
		release.countDown();

		assertThat(second.getResponse().getStatus()).isEqualTo(409);
		assertThat(second.getResponse().getContentAsString()).contains("TURN_ALREADY_IN_PROGRESS");
		assertThat(first.get(10, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(201);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM utterance_turn WHERE session_id = ?", Integer.class, sessionId))
				.isEqualTo(1);

		// 처리가 끝나면 다음 턴은 정상으로 받는다
		assertThat(submit(user, sessionId).getResponse().getStatus()).isEqualTo(201);
	}

	private MockHttpSession signupAndStart() throws Exception {
		MvcResult signup = mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
						.content("{\"loginId\":\"double01\",\"password\":\"password1234\",\"displayName\":\"테스터\"}"))
				.andExpect(status().isCreated()).andReturn();
		MockHttpSession user = (MockHttpSession) signup.getRequest().getSession(false);
		mockMvc.perform(post("/api/training-sessions").session(user)
						.contentType(MediaType.APPLICATION_JSON).content("{\"cardId\":1,\"intensity\":1}"))
				.andExpect(status().isCreated());
		return user;
	}

	private MvcResult submit(MockHttpSession user, long sessionId) {
		try {
			return mockMvc.perform(post("/api/training-sessions/" + sessionId + "/turns").session(user)
					.contentType("audio/webm").content(new byte[]{1, 2, 3})).andReturn();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
