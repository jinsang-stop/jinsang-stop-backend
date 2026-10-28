# jinsang-stop-backend

「진상 멈춰」 백엔드 (Java Spring + MySQL).

- 도메인 문서 · PRD · 슬라이스: [jinsang-stop/jinsang-stop](https://github.com/jinsang-stop/jinsang-stop)
- 작업 규약: [CONTRIBUTING.md](CONTRIBUTING.md)

## 스택

| 항목 | 값 |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.2 (Gradle 9.8.2 wrapper) |
| DB | MySQL 8.4 — 스키마·시드는 Flyway(`src/main/resources/db/migration`) |
| API 문서 | springdoc-openapi 3.1.1 |
| 테스트 DB | H2 인메모리(MySQL 호환 모드), 같은 Flyway 마이그레이션을 돌린다 |

## 실행 방법

### 1. 로컬 MySQL 띄우기

```bash
docker compose up -d
```

`compose.yaml`이 `localhost:3306`에 MySQL 8.4를 띄운다(DB `jinsangstop`, 계정 `jinsangstop`).
PC에 MySQL·MariaDB 서비스가 깔려 있어 3306을 이미 쓰고 있으면 먼저 멈춘다(관리자 PowerShell에서 `Stop-Service MySQL80`).
비밀번호는 로컬 개발 전용 값이다.

### 2. 애플리케이션 실행

```bash
./gradlew bootRun
```

처음 뜰 때 Flyway가 `scenario_card`·`user_account`·`training_session`·`utterance_turn` 테이블을 만들고 샘플 `공식 카드` 2장을 넣는다.

- API 문서: http://localhost:8080/swagger-ui
- OpenAPI JSON: http://localhost:8080/v3/api-docs

### 3. 테스트

```bash
./gradlew test
```

MySQL 없이 H2로 돈다.

### 환경변수

값이 없으면 `application.yml` 기본값(위 로컬 MySQL)을 쓴다. 예시는 `.env.example`.

| 변수 | 뜻 | 기본값 |
|---|---|---|
| `DB_URL` | JDBC 주소 | `jdbc:mysql://localhost:3306/jinsangstop?...` |
| `DB_USERNAME` / `DB_PASSWORD` | DB 계정 | 로컬 개발용 값 |
| `SERVER_PORT` | 서버 포트 | `8080` |
| `CORS_ALLOWED_ORIGINS` | 허용할 프론트 출처(쉼표 구분) | `http://localhost:5173` |
| `SESSION_TIMEOUT` | 로그인 세션 유지 시간 | `1d` |
| `VOICE_SERVICE_URL` | `음성 서비스` 주소 | `http://127.0.0.1:8000` |
| `VOICE_CONNECT_TIMEOUT` / `VOICE_HEALTH_TIMEOUT` / `VOICE_TRANSCRIBE_TIMEOUT` | 연결 · 헬스 · 전사 시간 제한 | `2s` / `3s` / `60s` |
| `TRAINING_IDLE_TIMEOUT` | 방치된 세션으로 보는 시간 | `10m` |
| `TRAINING_MAX_AUDIO_BYTES` | 발화 턴 오디오 최대 크기 | `26214400`(25MB) |
| `INFERENCE_SERVICE_URL` | `추론 서비스` 주소 | `http://127.0.0.1:8081` |
| `INFERENCE_CONNECT_TIMEOUT` / `INFERENCE_HEALTH_TIMEOUT` / `INFERENCE_COMPLETION_TIMEOUT` | 연결 · 헬스 · 생성 시간 제한 | `2s` / `3s` / `60s` |
| `ACTING_TEMPERATURE` / `ACTING_MAX_TOKENS` | 연기 temperature · 최대 토큰 | `0.8` / `200` |
| `ACTING_WARMUP_ENABLED` | 세션 시작 시 연기 프롬프트 캐시 예열 | `true` |
| `ABSOLUTE_LINES_DOCUMENT` | 절대선 문서 위치 | `classpath:safety/absolute-lines.txt` |
| `SCORING_SEED` / `SCORING_MAX_TOKENS` | 채점 고정 시드 · 최대 토큰 | `42` / `300` |
| `SCORING_MODE` | 채점 호출 시점 `PARALLEL` / `SEQUENTIAL` | `PARALLEL` |
| `SCORING_RESPONSE_WAIT` | 턴 응답에 점수를 싣기 위해 더 기다리는 시간 | `1s` |
| `SCORING_WARMUP_ENABLED` | 기동 시 루브릭 캐시 예열 | `true` |

## API 요약

| 메서드 · 경로 | 내용 | 로그인 |
|---|---|---|
| `POST /api/auth/signup` | 회원가입 — 가입과 동시에 로그인 | 불필요 |
| `POST /api/auth/login` | 로그인 — `Set-Cookie: JSESSIONID` | 불필요 |
| `POST /api/auth/logout` | 로그아웃 — 세션·쿠키 삭제 | 불필요 |
| `GET /api/auth/me` | 현재 사용자 | 필요 |
| `GET /api/cards` | `공식 카드` 목록 | 불필요 |
| `GET /api/cards/{cardId}` | `공식 카드` 상세 | 불필요 |
| `GET /api/intensities` | `강도` 기본값 · 공통 안내 · 단계별 허용 행동 · 고지 확인 필요 여부 | 불필요 |
| `POST /api/training-sessions` | `훈련 세션` 시작 `{cardId, intensity}` | 필요 |
| `POST /api/training-sessions/{sessionId}/turns` | `발화 턴` 제출 — 본문 = 오디오 바이트(`audio/*`). 응답에 `민원인` 대사·합성 음성(Base64 WAV)·진정 여부·`태도 3축` 점수 | 필요 |
| `GET /api/training-sessions/{sessionId}/turns/{turnNumber}/score` | 턴 점수 — 채점이 턴 응답보다 늦을 때 다시 받는다 | 필요 |
| `GET /api/training-sessions/current` | 내 진행 중 세션 — 없으면 204. 세션 식별자를 잃었을 때 이어 하기·중단 | 필요 |
| `GET /api/training-sessions/{sessionId}` | 세션 상태·종료 사유 — 새로고침 뒤 복원 | 필요 |
| `POST /api/training-sessions/{sessionId}/abort` | 사용자 중단 — 즉시 종료 | 필요 |

카드 응답에는 진정 조건과 양보 불가선이 **필드 자체로 없다**. DB에만 있고 `민원인` 연기 프롬프트 조립에만 쓴다.

### 인증

자체 아이디·비밀번호 + 서버 세션 쿠키다([ADR-0010](https://github.com/jinsang-stop/jinsang-stop/blob/main/docs/adr/0010-id-password-session-cookie-auth.md)).

- 로그인하면 `JSESSIONID` 쿠키(HttpOnly, SameSite=Lax)가 내려간다. 프론트는 요청마다 `credentials: 'include'`만 켜면 된다.
- 위 표에서 "불필요"로 적힌 API를 뺀 나머지는 전부 로그인이 필요하다. 로그인 없이 부르면 `401 UNAUTHORIZED`.
- 비밀번호는 BCrypt 해시로만 저장한다. 세션은 서버 메모리에 있어 서버를 재시작하면 다시 로그인해야 한다.
- 컨트롤러에서 현재 사용자는 `@AuthenticationPrincipal LoginUser loginUser`로 받는다(`loginUser.getUserId()`).

### 훈련 세션 · 발화 턴

- 시연 장비 한 대가 **한 번에 한 세션만** 돌린다. 진행 중 세션은 `training_session.active_slot = 1` 하나뿐이고 유니크 제약이 동시 시작을 막는다.
  마지막 활동 뒤로 `TRAINING_IDLE_TIMEOUT`(기본 10분)이 지난 세션은 방치된 것으로 보고 다음 시작 요청 때 `ABANDONED`로 정리한다.
- 세션 시작 전에 `음성 서비스` `/health`를 본다. 200이 아니면 세션을 만들지 않고 `503 TRAINING_ENVIRONMENT_NOT_READY`.
- 턴 제출은 multipart가 아니라 **요청 본문에 오디오 바이트 그대로** 받는다. multipart는 큰 파일을 디스크 임시 파일로 쓰기 때문이다(ADR-0006). `spring.servlet.multipart.enabled=false`.
- 오디오는 메모리에서만 다루고 음성 서비스로 그대로 넘긴 뒤 버린다. DB에는 전사 텍스트만 남는다.
- 빈 전사는 `422 EMPTY_TRANSCRIPT`이고 턴 번호·`turn_count`를 소모하지 않는다. `음성 서비스` 422는 `AUDIO_UNREADABLE`(다시 말하기), 503·연결 실패는 `TRAINING_ENVIRONMENT_NOT_READY`(환경 문제)로 바꾼다.
- 로컬에서 실제로 돌려 보려면 `jinsang-stop-voice`의 `음성 서비스`(`python -m app.main`, `127.0.0.1:8000`)와 `추론 서비스`(`scripts/run-inference-service.ps1`, `127.0.0.1:8081`)를 띄운다.

### 민원인 연기 · 출력 검사

턴 하나는 전사(`음성 서비스`) → 연기(`추론 서비스`) → `출력 검사` → 합성(`음성 서비스`) → 저장 순서다.

- **연기 프롬프트**는 `ComplainantPromptAssembler`가 조립한다 — 카드(상황·페르소나·요구·양보 불가선·진정 조건), `강도` 단계의 허용 행동 열거("열거되지 않은 행동은 하지 않는다"), 절대선, 말투, 출력 형식(`{"대사", "진정함"}`). 이전 턴은 대화 기록으로 넣는다.
- **연기 호출**은 슬롯 0, 추론 모드 끔(`chat_template_kwargs.enable_thinking=false`), temperature 0.8, JSON 스키마. 시드는 세션마다 `training_session.acting_seed`에 기록하고 턴·시도마다 `시드 + 턴×10 + 시도`를 쓴다.
- **출력 검사**는 [절대선 문서](src/main/resources/safety/absolute-lines.txt)의 금칙 표현(띄어쓰기 무시) + 한국어 검사(한자·가나 금지, 라틴 문자 비율 제한). 형식이 깨진 응답과 `직원:`을 따라 쓴 응답(역할 혼동)도 실패로 본다. 실패하면 그 턴만 1회 다시 생성하고, 또 실패하면 세션을 `SYSTEM_ERROR`로 끝낸다(`500 SESSION_ENDED_BY_SYSTEM_ERROR`). 걸린 대사는 합성하지도, 로그에 남기지도 않는다.
- **절대선 문서는 초안**이다. 표현을 추가하면 `OutputCheckerTest`가 문서를 그대로 읽어 함께 검사한다.
- 세션 시작 시 `음성 서비스`와 `추론 서비스` `/health`를 모두 본다. 시작 직후 연기 시스템 프롬프트를 슬롯 0에 비동기로 올려 둔다(캐시 예열, `ACTING_WARMUP_ENABLED`).
- 턴마다 `턴 지연: totalMs, transcribeMs, actMs, actAttempts, synthesizeMs` 로그를 남긴다.
- **강도 상한 테스트**: 실제 `추론 서비스`를 띄우고 아래를 돌린다. 저장 도발 입력(`src/test/resources/provocations/intensity-cap.txt`)을 강도 1~3에 보내 출력 검사 전 원본에 절대선 표현이 나오는지 보고, 결과 표를 `build/reports/intensity-cap.md`에 남긴다.

```bash
JINSANGSTOP_RUN_INFERENCE_IT=1 ./gradlew test --tests "*IntensityCapIntegrationTest"
```

### 태도 3축 채점

- 축마다 **1~5 정수 + 80자 이내 근거**. 세 축은 독립이며 합산 점수는 어디에도 없다(ADR-0002, ADR-0004).
- 루브릭은 `ScoringPromptAssembler.RUBRIC`. 직무 조치의 옳고 그름은 채점하지 않는다고 명시하고, 회피·침묵은 정서 안정 가점 없음, 과잉 사과·요구 수용은 공감 인정 아님, 공감 없는 단호한 거절도 경계 설정 가점이라고 적었다.
- 채점은 직전 `민원인` 대사와 이번 직원 발화만 본다 — 이번 턴의 `민원인` 대사, 카드의 진정 조건·양보 불가선은 모른다.
- 호출은 슬롯 1, temperature 0, 고정 시드(`SCORING_SEED`), JSON 스키마. 응답 범위·필수 값은 `AttitudeScorer`가 다시 검증하고, 하나라도 어기거나 호출이 실패하면 `FAILED` — 점수는 NULL(정보 없음)이지 0이 아니다.
- 기본은 연기와 **병렬**(`SCORING_MODE=PARALLEL`). `SEQUENTIAL`이면 연기 응답 직후 시작해 합성 구간과 겹친다.
- 합성이 끝난 뒤 `SCORING_RESPONSE_WAIT`(기본 1초)까지 기다려 끝나면 턴 응답에 싣고, 아니면 `PENDING`으로 응답한 뒤 끝나는 대로 저장한다. 프론트는 점수 조회 API로 다시 받는다.
- 서버가 뜨면 루브릭을 슬롯 1에 캐시 예열한다(`SCORING_WARMUP_ENABLED`).
- **저장 발화 채점 테스트**(재현성 → 축 독립성 → 직무 조치 비채점): 실제 `추론 서비스`를 띄우고 아래를 돌린다. 발화 묶음은 `src/test/resources/scoring/saved-utterances.txt`, 결과는 `build/reports/scoring.md`.

```bash
JINSANGSTOP_RUN_INFERENCE_IT=1 ./gradlew test --tests "*ScoringIntegrationTest"
```

### 종료 판정

`training_session.status`가 진행 중이 아니면 그 값이 곧 종료 사유이고, 세션마다 하나만 기록된다.

| 상태 | 뜻 | `turns_to_calm` |
|---|---|---|
| `CALMED` | `진정 종료` — 턴 응답의 진정 여부가 참 | 그 턴 번호 |
| `TURN_LIMIT_REACHED` | 진정시키지 못한 채 종료 — `하드 턴 상한` 도달 | NULL |
| `ABORTED` | 사용자 중단(`POST .../abort`) | NULL |
| `SYSTEM_ERROR` | 시스템 오류로 중단 — 출력 검사 재시도 실패 | NULL |
| `ABANDONED` | 방치되어 다음 시작 요청 때 정리됨 | NULL |

- 판정은 턴을 저장하는 트랜잭션 안에서 한다. 마지막 턴에서 진정하면 상한보다 `진정 종료`가 우선한다.
- 끝나면 `active_slot`을 비워 바로 새 세션을 시작할 수 있다. 종료된 세션에 턴을 내면 `409 TRAINING_SESSION_NOT_IN_PROGRESS`.
- 중단하면 처리 중이던 턴은 저장 단계에서 세션이 끝난 것을 보고 버려진다(그 턴 요청은 409).
- 채점이 종료 뒤에 끝나도 점수는 저장한다(리포트에 쓰인다).

### 오류 응답 형식

모든 API가 같은 형식을 쓴다. 프론트엔드는 `code`로 분기하고 `message`를 그대로 띄운다.

```json
{ "status": 404, "code": "CARD_NOT_FOUND", "message": "시나리오 카드를 찾을 수 없습니다." }
```

| `code` | 상태 | 언제 |
|---|---|---|
| `INVALID_REQUEST` | 400 | 본문 검증 실패(무엇이 틀렸는지 `message`에 담김), 경로 변수 타입 불일치, 깨진 JSON |
| `UNAUTHORIZED` | 401 | 로그인 없이 보호된 API 호출 |
| `LOGIN_FAILED` | 401 | 아이디 또는 비밀번호 불일치 |
| `NOT_FOUND` | 404 | 없는 경로 |
| `CARD_NOT_FOUND` | 404 | 없는 카드 |
| `DUPLICATE_LOGIN_ID` | 409 | 이미 쓰는 아이디로 가입 |
| `METHOD_NOT_ALLOWED` | 405 | 지원하지 않는 HTTP 메서드 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | 본문 형식이 맞지 않음(턴 제출은 `audio/*`) |
| `TRAINING_SESSION_BUSY` | 409 | 다른 훈련이 진행 중 |
| `TRAINING_SESSION_NOT_FOUND` | 404 | 없거나 남의 세션 |
| `TURN_NOT_FOUND` | 404 | 세션에 그 번호의 턴이 없음 |
| `TRAINING_SESSION_NOT_IN_PROGRESS` | 409 | 이미 끝난 세션에 턴 제출 |
| `TURN_ALREADY_IN_PROGRESS` | 409 | 같은 세션의 이전 턴을 처리 중(중복 제출) |
| `EMPTY_TRANSCRIPT` | 422 | 전사가 비었음 — 턴 미소모 |
| `AUDIO_UNREADABLE` | 422 | 음성 서비스가 오디오를 읽지 못함 |
| `AUDIO_TOO_LARGE` | 413 | 오디오가 `TRAINING_MAX_AUDIO_BYTES` 초과 |
| `SESSION_ENDED_BY_SYSTEM_ERROR` | 500 | `민원인` 대사가 재시도까지 출력 검사 실패 — 세션 종료 |
| `TRAINING_ENVIRONMENT_NOT_READY` | 503 | 음성·추론 서비스 미준비·장애(턴 미소모) |
| `INTERNAL_ERROR` | 500 | 처리하지 못한 오류 |

새 오류는 `common/error/ErrorCode`에 추가한다.

## 식별자 대응표

`CONTEXT.md` 용어를 코드에서 이렇게 쓴다. `_Avoid_` 동의어(NPC, 난이도 등)는 쓰지 않는다.

| 용어 | 클래스 · 필드 | DB |
|---|---|---|
| 시나리오 카드 | `ScenarioCard` | `scenario_card` |
| 공식 카드 | `CardType.OFFICIAL` | `card_type = 'OFFICIAL'` |
| 상황 설정 | `situation` | `situation` |
| 민원인 | `complainant` (접두어) | `complainant_*` |
| 민원인 화면 표시 역할명 | `complainantRoleName` | `complainant_role_name` |
| 민원인 페르소나 | `complainantPersona` | `complainant_persona` |
| 민원인의 실제 요구 | `complainantDemand` | `complainant_demand` |
| 양보 불가선 | `nonNegotiableLine` | `non_negotiable_line` |
| 진정 조건 | `calmingCondition` | `calming_condition` |
| 하드 턴 상한 | `hardTurnLimit` | `hard_turn_limit` |
| 강도 | `Intensity` (`LEVEL_1`~`LEVEL_3`) | — |
| 사용자 계정 | `UserAccount` (세션 안에서는 `LoginUser`) | `user_account` |
| 훈련 세션 | `TrainingSession` | `training_session` |
| 발화 턴 | `UtteranceTurn` | `utterance_turn` |
| 진정 종료 · 진정시키지 못한 채 종료 · 사용자 중단 · 시스템 오류로 중단 | `TrainingSessionStatus.CALMED` · `TURN_LIMIT_REACHED` · `ABORTED` · `SYSTEM_ERROR` | `training_session.status` |
| 소요 턴 수 | `turnsToCalm` | `training_session.turns_to_calm` |
| 음성 서비스 | `VoiceClient` / `HttpVoiceClient` | — |
| 추론 서비스 | `InferenceClient` / `HttpInferenceClient` | — |
| 출력 검사 | `OutputChecker` (금칙 목록 `AbsoluteLineList`) | — |
| 민원인 연기 | `ComplainantActor` / `ComplainantPromptAssembler` | — |
| 태도 3축 | `AttitudeAxis`, `AttitudeScores` — 정서 안정 `emotionalStability` · 공감 인정 `empathyAcknowledgement` · 경계 설정 `boundarySetting` | `utterance_turn.*_score`, `*_reason` |
| 채점 | `AttitudeScorer` / `ScoringPromptAssembler`, 상태 `ScoreStatus`(PENDING·SCORED·FAILED) | `utterance_turn.score_status` |

이후 슬라이스에서 쓸 이름도 같은 규칙을 따른다 — `성장 기록` `GrowthRecord`.

## 패키지 구조

```
com.jinsangstop
├── auth            회원가입·로그인·현재 사용자 (domain · dto · security · service · controller)
├── common
│   ├── config      CORS · OpenAPI · Security
│   └── error       ErrorCode · ErrorResponse · 전역 예외 처리
├── card            시나리오 카드 (domain · dto · service · controller)
├── intensity       강도 (domain · dto · controller)
├── complainant     민원인 연기 프롬프트 조립 · 출력 검사(safety)
├── inference       추론 서비스 HTTP 클라이언트
├── scoring         태도 3축 채점 루브릭 · 채점기
├── training        훈련 세션 · 발화 턴 (domain · dto · service · controller)
└── voice           음성 서비스 HTTP 클라이언트
```
