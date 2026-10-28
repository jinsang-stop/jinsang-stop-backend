package com.jinsangstop.training;

import com.jinsangstop.common.config.AsyncConfig;
import com.jinsangstop.inference.InferenceClient;
import com.jinsangstop.support.TestDataCleaner;
import com.jinsangstop.voice.VoiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실행기 대기열이 가득 차 작업을 거절해도 세션 시작·턴 처리가 500으로 끝나지 않는지 확인한다.
 * 캐시 예열은 건너뛰고(#4), 채점은 정보 없음으로 처리된다(#5).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "jinsangstop.acting.warmup-enabled=true")
class ExecutorRejectionTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoBean
	private VoiceClient voiceClient;

	@MockitoBean
	private InferenceClient inferenceClient;

	/** 모든 작업을 거절하는 실행기 */
	@MockitoBean(name = AsyncConfig.TURN_EXECUTOR)
	private ThreadPoolTaskExecutor turnExecutor;

	@BeforeEach
	void setUp() {
		TestDataCleaner.clearUserData(jdbcTemplate);
		when(voiceClient.isReady()).thenReturn(true);
		when(inferenceClient.isReady()).thenReturn(true);
		when(voiceClient.transcribe(any(), anyString())).thenReturn("죄송합니다 손님");
		when(voiceClient.synthesize(anyString())).thenReturn("RIFF".getBytes());
		when(inferenceClient.complete(any())).thenReturn("{\"대사\": \"환불해 줘요\", \"진정함\": false}");
		doThrow(new TaskRejectedException("가득 참")).when(turnExecutor).execute(any(Runnable.class));
	}

	@Test
	@DisplayName("캐시 예열을 실행기에 올리지 못해도 세션 시작은 201")
	void warmupRejectedStillStarts() throws Exception {
		MvcResult signup = mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
						.content("{\"loginId\":\"reject01\",\"password\":\"password1234\",\"displayName\":\"테스터\"}"))
				.andExpect(status().isCreated()).andReturn();
		MockHttpSession user = (MockHttpSession) signup.getRequest().getSession(false);

		mockMvc.perform(post("/api/training-sessions").session(user)
						.contentType(MediaType.APPLICATION_JSON).content("{\"cardId\":1,\"intensity\":1}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"));
	}
}
