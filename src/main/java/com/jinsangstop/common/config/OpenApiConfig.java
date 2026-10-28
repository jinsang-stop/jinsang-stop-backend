package com.jinsangstop.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * `/swagger-ui`에 표시될 API 문서의 머리말을 정한다.
 * 프론트엔드가 문서 첫 화면만 읽고도 연동 순서·인증·오류 처리·화면 규칙을 알 수 있도록 안내문을 함께 싣는다.
 */
@Configuration
public class OpenApiConfig {

	/** 태그 이름 — 컨트롤러의 @Tag와 같은 값을 써야 한 그룹으로 묶인다 */
	public static final String TAG_AUTH = "1. 인증";
	public static final String TAG_INTENSITY = "2. 강도";
	public static final String TAG_CARD = "3. 시나리오 카드";
	public static final String TAG_TRAINING = "4. 훈련 세션";
	public static final String TAG_REPORT = "5. 리포트";
	public static final String TAG_GROWTH = "6. 성장 기록";

	/** 세션 쿠키 인증 스킴 이름 — 로그인이 필요한 API에 @SecurityRequirement로 붙인다 */
	public static final String SESSION_COOKIE_SCHEME = "sessionCookie";

	/** 문서 첫 화면에 보이는 프론트엔드 연동 안내(마크다운) */
	private static final String DESCRIPTION = """
			악성 민원 응대를 음성으로 반복 연습하는 훈련 서비스 「진상 멈춰」의 백엔드 API입니다.

			## 인증 — 세션 쿠키
			| 단계 | 호출 | 결과 |
			|---|---|---|
			| 앱 시작·새로고침 | `GET /api/auth/me` | 200이면 로그인 상태 복원, 401이면 로그인 화면 |
			| 가입 | `POST /api/auth/signup` | 201 + **가입과 동시에 로그인** |
			| 로그인 | `POST /api/auth/login` | 200 + `Set-Cookie: JSESSIONID` |
			| 로그아웃 | `POST /api/auth/logout` | 204, 세션 쿠키 삭제 |

			- 쿠키는 HttpOnly라 JS에서 읽을 수 없고 읽을 필요도 없다. **모든 요청에 `credentials: 'include'`**(axios는 `withCredentials: true`)만 켜면 브라우저가 알아서 보낸다.
			- 로그인이 필요한 API를 로그인 없이 부르면 `401 UNAUTHORIZED` — 로그인 화면으로 보낸다.
			- 공식 카드·강도 조회는 로그인 없이도 된다.
			- 서버를 재시작하면 세션이 사라져 다시 로그인해야 한다(ADR-0010).
			- Swagger에서 시험할 때는 로그인 API를 먼저 실행하면 같은 브라우저 쿠키로 나머지 API가 동작한다.

			## 연동 순서 — 카드 선택 화면
			| 단계 | 화면 | 호출 |
			|---|---|---|
			| 1 | 공식 카드 목록 | `GET /api/cards` |
			| 2 | 카드 상세 | `GET /api/cards/{cardId}` |
			| 3 | 강도 선택 | `GET /api/intensities` — 앱 시작 시 한 번 받아 두어도 된다 |
			| 4 | 고지 확인 | 고른 단계의 `requiresConsent`가 `true`(2·3단계)면 `allowedBehaviors`를 보여 주고 확인을 받은 뒤에만 다음으로 넘어간다 |

			강도 선택의 초기값은 `defaultLevel`(= 1)을 쓴다. 숫자를 프론트에 박지 않는다.

			## 연동 순서 — 훈련 화면 (로그인 필요)
			| 단계 | 화면 | 호출 | 실패 시 안내 |
			|---|---|---|---|
			| 1 | 훈련 시작 | `POST /api/training-sessions` `{cardId, intensity}` | `409 TRAINING_SESSION_BUSY` 대기 안내 · `503 TRAINING_ENVIRONMENT_NOT_READY` 환경 미준비 |
			| 2 | 마이크 권한 | 브라우저 `getUserMedia` | — |
			| 3 | 말하기 → "말하기 끝내기" | `POST /api/training-sessions/{sessionId}/turns` (본문 = 녹음 Blob) | `422 EMPTY_TRANSCRIPT`·`AUDIO_UNREADABLE` 다시 말하기 |
			| 4 | 응답 표시 | `userTranscript`(내 말) · `complainantLine`(역할명과 함께) 표시, `complainantAudio` 재생 | `500 SESSION_ENDED_BY_SYSTEM_ERROR` 종료 안내 |
			| 5 | 태도 3축 갱신 | 응답의 `score`. `PENDING`이면 `GET /api/training-sessions/{sessionId}/turns/{n}/score`를 잠시 뒤 다시 | `FAILED`면 "채점되지 않음"(마지막 성공값 유지) |
			| 6 | 종료 | 턴 응답 `sessionStatus`가 `CALMED`·`TURN_LIMIT_REACHED`면 종료 화면. 중단 버튼은 `POST /api/training-sessions/{sessionId}/abort`(확인창 없이) | — |

			종료 화면 → 리포트: `GET /api/training-sessions/{sessionId}/report` — 턴 타임라인, 축별 점수, 종료 사유, 성장 기록 미편입 사유.
			성장 그래프: `GET /api/growth-records?cardId=&intensity=` — 강도마다 따로 부르고, 강도가 다른 점을 한 선으로 잇지 않는다. 같은 카드 재도전은 같은 `cardId`·`intensity`로 세션을 다시 시작한다.

			음성·추론 서비스는 브라우저에서 직접 부르지 않는다. 사용자 오디오와 민원인 합성 음성은 서버에 저장되지 않는다.

			## 오류 응답
			성공이 아니면 **모든 API가 같은 형식**을 돌려준다. `code`로 분기하고, `message`는 사용자에게 그대로 보여 줘도 되는 문장이다.
			```json
			{ "status": 404, "code": "CARD_NOT_FOUND", "message": "시나리오 카드를 찾을 수 없습니다." }
			```
			| code | status | 언제 |
			|---|---|---|
			| `INVALID_REQUEST` | 400 | 입력값 검증 실패(무엇이 틀렸는지 `message`에 담김), 경로 변수 형식 오류, 본문 JSON 오류 |
			| `UNAUTHORIZED` | 401 | 로그인이 필요한 API를 로그인 없이 호출 |
			| `LOGIN_FAILED` | 401 | 아이디 또는 비밀번호 불일치 |
			| `NOT_FOUND` | 404 | 없는 주소 |
			| `CARD_NOT_FOUND` | 404 | 없는 카드 |
			| `DUPLICATE_LOGIN_ID` | 409 | 가입 시 이미 쓰는 아이디 |
			| `TRAINING_SESSION_BUSY` | 409 | 다른 훈련이 진행 중 — 대기 안내 |
			| `TRAINING_SESSION_NOT_FOUND` | 404 | 없거나 남의 세션 |
			| `TURN_NOT_FOUND` | 404 | 세션에 그 번호의 턴이 없음 |
			| `TRAINING_SESSION_IN_PROGRESS` | 409 | 진행 중인 세션의 리포트 요청 |
			| `TRAINING_SESSION_NOT_IN_PROGRESS` | 409 | 이미 끝난 세션에 턴 제출 |
			| `TURN_ALREADY_IN_PROGRESS` | 409 | 같은 세션의 이전 턴을 처리 중 — 버튼을 두 번 누른 경우. 무시해도 된다 |
			| `EMPTY_TRANSCRIPT` | 422 | 말소리가 없음 — 턴 미소모, 다시 말하기 |
			| `AUDIO_UNREADABLE` | 422 | 녹음을 읽지 못함 — 다시 말하기 |
			| `AUDIO_TOO_LARGE` | 413 | 녹음이 너무 김 |
			| `METHOD_NOT_ALLOWED` | 405 | 지원하지 않는 HTTP 메서드 |
			| `UNSUPPORTED_MEDIA_TYPE` | 415 | 본문 형식이 맞지 않음(턴 제출은 audio/*) |
			| `SESSION_ENDED_BY_SYSTEM_ERROR` | 500 | 민원인 대사가 안전 검사를 통과하지 못해 세션 종료 — 새 훈련으로 |
			| `TRAINING_ENVIRONMENT_NOT_READY` | 503 | 음성·추론 서비스 미준비·장애 — 다시 말해도 풀리지 않음 |
			| `INTERNAL_ERROR` | 500 | 서버 내부 오류 |

			## 꼭 지킬 것
			- 카드 응답에는 진정 조건·양보 불가선이 **없다**. 프론트 타입에도 두지 않는다 — 사용자가 정답을 보고 연기하게 되기 때문이다.
			- 상대역은 화면에 `complainantRoleName`(손님 등)으로 표시한다. 코드에서는 언제나 `민원인`(complainant)이다.
			- 태도 3축(정서 안정·공감 인정·경계 설정, 각 1~5)을 **합산한 종합 점수를 만들지 않는다.** 채점 실패는 0점이 아니라 "채점되지 않음"이다.
			- 화면 문구에 "정답 대응", "매뉴얼", "올바른 조치"를 쓰지 않는다. 이 서비스는 조치의 옳고 그름을 판정하지 않는다.

			## 로컬 연동
			- 기본 주소 `http://localhost:8080`, CORS 허용 출처 기본값 `http://localhost:5173`(Vite 기본 포트)
			""";

	/** 문서 머리말 정보 */
	@Bean
	public OpenAPI jinsangStopOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("진상 멈춰 API")
						.description(DESCRIPTION)
						.version("v1"))
				.servers(List.of(new Server().url("http://localhost:8080").description("로컬 개발 서버")))
				.components(new Components().addSecuritySchemes(SESSION_COOKIE_SCHEME, new SecurityScheme()
						.type(SecurityScheme.Type.APIKEY)
						.in(SecurityScheme.In.COOKIE)
						.name("JSESSIONID")
						.description("로그인하면 서버가 내려주는 HttpOnly 세션 쿠키. 브라우저가 자동으로 보낸다.")))
				.tags(List.of(
						new Tag().name(TAG_AUTH).description("회원가입·로그인·로그아웃·현재 사용자 — 세션 쿠키 방식"),
						new Tag().name(TAG_INTENSITY).description("강도 선택 화면 — 기본값, 단계별로 민원인이 할 수 있는 행동, 고지 확인 필요 여부"),
						new Tag().name(TAG_CARD).description("공식 카드 목록·상세 화면 — 진정 조건·양보 불가선은 내려가지 않는다"),
						new Tag().name(TAG_TRAINING).description("훈련 세션 시작·조회·중단, 발화 턴 제출, 턴 점수 — 로그인 필요"),
						new Tag().name(TAG_REPORT).description("세션 리포트 — 턴 타임라인, 종료 사유, 성장 기록 미편입 사유"),
						new Tag().name(TAG_GROWTH).description("성장 기록 — 같은 카드·강도의 소요 턴 수 시계열")));
	}
}
