package com.jinsangstop.journey;

import com.jinsangstop.inference.InferenceClient;
import com.jinsangstop.scoring.AttitudeScorer;
import com.jinsangstop.support.TestDataCleaner;
import com.jinsangstop.voice.VoiceClient;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 대표 여정(PRD v2 E2E, 슬라이스 #9) — API만으로 끝까지 간다.
 * 로그인 → 공식 카드 선택 → 강도 1 → 발화 3턴 → 진정 종료 → 리포트 → 같은 카드 재도전 → 성장 기록에 점 두 개.
 * 음성·추론 서비스는 저장한 응답으로 대체한다. 브라우저 E2E는 프론트엔드 파트가 자동화한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "jinsangstop.growth.boundary-threshold=3.0")
class RepresentativeJourneyTest {

	/** 저장 응답 — 세 번째 턴에서 진정한다 */
	private static final String[] ACTING = {
			"{\"대사\": \"어제 산 도시락이 상했어요. 당장 현금으로 환불해 주세요.\", \"진정함\": false}",
			"{\"대사\": \"영수증 같은 거 없다니까요? 단골인데 이러기예요?\", \"진정함\": false}",
			"{\"대사\": \"알겠어요. 그럼 고객센터로 접수해 주세요.\", \"진정함\": true}"
	};
	private static final String[] TRANSCRIPTS = {
			"어서 오세요, 무엇을 도와드릴까요?",
			"상한 도시락 때문에 불편하셨겠어요. 영수증이 없으면 현금 환불은 어렵지만 카드 결제 내역으로 확인해 드릴 수 있어요.",
			"현금 환불은 어렵지만 본사 고객센터에 바로 접수해 드릴게요."
	};
	private static final String SCORE = """
			{"정서안정": {"점수": 4, "근거": "차분했다."}, "공감인정": {"점수": 4, "근거": "불편을 인정했다."}, "경계설정": {"점수": 4, "근거": "안 되는 것을 분명히 했다."}}""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoBean
	private VoiceClient voiceClient;

	@MockitoBean
	private InferenceClient inferenceClient;

	/** 세션 안에서 몇 번째 턴인지 — 저장 응답을 고른다 */
	private final AtomicInteger turnIndex = new AtomicInteger();

	@BeforeEach
	void setUp() {
		TestDataCleaner.clearUserData(jdbcTemplate);
		when(voiceClient.isReady()).thenReturn(true);
		when(inferenceClient.isReady()).thenReturn(true);
		when(voiceClient.synthesize(anyString())).thenReturn("RIFF".getBytes());
		when(voiceClient.transcribe(any(), anyString())).thenAnswer(invocation -> TRANSCRIPTS[turnIndex.get() % 3]);
		doAnswer(invocation -> {
			InferenceClient.ChatRequest request = invocation.getArgument(0);
			if (request.slot() == AttitudeScorer.SCORING_SLOT) {
				return SCORE;
			}
			return ACTING[turnIndex.get() % 3];
		}).when(inferenceClient).complete(any());
	}

	@Test
	@DisplayName("로그인 → 카드 → 강도 1 → 3턴 → 진정 종료 → 리포트 → 재도전 → 성장 기록에 점 두 개")
	void journey() throws Exception {
		// 로그인(가입과 동시에 로그인)
		MvcResult signup = mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
						.content("{\"loginId\":\"journey01\",\"password\":\"password1234\",\"displayName\":\"김알바\"}"))
				.andExpect(status().isCreated()).andReturn();
		MockHttpSession user = (MockHttpSession) signup.getRequest().getSession(false);
		mockMvc.perform(get("/api/auth/me").session(user)).andExpect(jsonPath("$.displayName").value("김알바"));

		// 공식 카드 선택 · 강도 확인
		mockMvc.perform(get("/api/cards")).andExpect(jsonPath("$[0].cardId").value(1));
		mockMvc.perform(get("/api/intensities")).andExpect(jsonPath("$.defaultLevel").value(1));

		// 첫 도전
		long first = trainOnce(user);
		mockMvc.perform(get("/api/training-sessions/" + first + "/report").session(user))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.endReason").value("CALMED"))
				.andExpect(jsonPath("$.turnsToCalm").value(3))
				.andExpect(jsonPath("$.turns", hasSize(3)))
				.andExpect(jsonPath("$.growthRecord.included").value(true));

		// 같은 카드 재도전
		long second = trainOnce(user);

		// 성장 기록에 점 두 개
		mockMvc.perform(get("/api/growth-records").session(user).param("cardId", "1").param("intensity", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.points", hasSize(2)))
				.andExpect(jsonPath("$.points[0].sessionId").value(first))
				.andExpect(jsonPath("$.points[1].sessionId").value(second))
				.andExpect(jsonPath("$.points[1].turnsToCalm").value(3));
	}

	/** 카드 1·강도 1로 세션을 열고 3턴 만에 진정 종료시킨다 */
	private long trainOnce(MockHttpSession user) throws Exception {
		turnIndex.set(0);
		String started = mockMvc.perform(post("/api/training-sessions").session(user)
						.contentType(MediaType.APPLICATION_JSON).content("{\"cardId\":1,\"intensity\":1}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long sessionId = Long.parseLong(started.replaceAll(".*\"sessionId\":(\\d+).*", "$1"));

		for (int n = 1; n <= 3; n++) {
			mockMvc.perform(post("/api/training-sessions/" + sessionId + "/turns").session(user)
							.contentType("audio/webm;codecs=opus").content(new byte[]{1, 2, 3}))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.turnNumber").value(n))
					.andExpect(jsonPath("$.score.status").value("SCORED"))
					.andExpect(jsonPath("$.sessionStatus").value(n < 3 ? "IN_PROGRESS" : "CALMED"));
			turnIndex.incrementAndGet();
		}
		return sessionId;
	}
}
