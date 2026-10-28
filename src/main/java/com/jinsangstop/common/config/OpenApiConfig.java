package com.jinsangstop.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * `/swagger-ui`에 표시될 API 문서의 머리말을 정한다.
 * 프론트엔드가 문서 첫 화면만 읽고도 연동 순서·오류 처리·화면 규칙을 알 수 있도록 안내문을 함께 싣는다.
 */
@Configuration
public class OpenApiConfig {

	/** 태그 이름 — 컨트롤러의 @Tag와 같은 값을 써야 한 그룹으로 묶인다 */
	public static final String TAG_INTENSITY = "1. 강도";
	public static final String TAG_CARD = "2. 시나리오 카드";

	/** 문서 첫 화면에 보이는 프론트엔드 연동 안내(마크다운) */
	private static final String DESCRIPTION = """
			악성 민원 응대를 음성으로 반복 연습하는 훈련 서비스 「진상 멈춰」의 백엔드 API입니다.

			## 프론트엔드 연동 순서 — 카드 선택 화면
			| 단계 | 화면 | 호출 |
			|---|---|---|
			| 1 | 공식 카드 목록 | `GET /api/cards` |
			| 2 | 카드 상세 | `GET /api/cards/{cardId}` |
			| 3 | 강도 선택 | `GET /api/intensities` — 앱 시작 시 한 번 받아 두어도 된다 |
			| 4 | 고지 확인 | 고른 단계의 `requiresConsent`가 `true`(2·3단계)면 `allowedBehaviors`를 보여 주고 확인을 받은 뒤에만 다음으로 넘어간다 |

			강도 선택의 초기값은 `defaultLevel`(= 1)을 쓴다. 숫자를 프론트에 박지 않는다.

			## 오류 응답
			성공이 아니면 **모든 API가 같은 형식**을 돌려준다. `code`로 분기하고, `message`는 사용자에게 그대로 보여 줘도 되는 문장이다.
			```json
			{ "status": 404, "code": "CARD_NOT_FOUND", "message": "시나리오 카드를 찾을 수 없습니다." }
			```
			| code | status | 언제 |
			|---|---|---|
			| `INVALID_REQUEST` | 400 | 경로 변수 형식 오류, 요청 본문 검증 실패 |
			| `NOT_FOUND` | 404 | 없는 주소 |
			| `CARD_NOT_FOUND` | 404 | 없는 카드 |
			| `INTERNAL_ERROR` | 500 | 서버 내부 오류 |

			## 꼭 지킬 것
			- 카드 응답에는 진정 조건·양보 불가선이 **없다**. 프론트 타입에도 두지 않는다 — 사용자가 정답을 보고 연기하게 되기 때문이다.
			- 상대역은 화면에 `complainantRoleName`(손님 등)으로 표시한다. 코드에서는 언제나 `민원인`(complainant)이다.
			- 화면 문구에 "정답 대응", "매뉴얼", "올바른 조치"를 쓰지 않는다. 이 서비스는 조치의 옳고 그름을 판정하지 않는다.

			## 로컬 연동
			- 기본 주소 `http://localhost:8080`, CORS 허용 출처 기본값 `http://localhost:5173`(Vite 기본 포트)
			- 인증은 아직 없다(로그인 슬라이스에서 추가).
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
				.tags(List.of(
						new Tag().name(TAG_INTENSITY).description("강도 선택 화면 — 기본값, 단계별로 민원인이 할 수 있는 행동, 고지 확인 필요 여부"),
						new Tag().name(TAG_CARD).description("공식 카드 목록·상세 화면 — 진정 조건·양보 불가선은 내려가지 않는다")));
	}
}
