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

처음 뜰 때 Flyway가 `scenario_card` 테이블을 만들고 샘플 `공식 카드` 2장을 넣는다.

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

## API 요약

| 메서드 · 경로 | 내용 |
|---|---|
| `GET /api/cards` | `공식 카드` 목록 |
| `GET /api/cards/{cardId}` | `공식 카드` 상세 |
| `GET /api/intensities` | `강도` 기본값 · 공통 안내 · 단계별 허용 행동 · 고지 확인 필요 여부 |

카드 응답에는 진정 조건과 양보 불가선이 **필드 자체로 없다**. DB에만 있고 `민원인` 연기 프롬프트 조립에만 쓴다.

### 오류 응답 형식

모든 API가 같은 형식을 쓴다. 프론트엔드는 `code`로 분기하고 `message`를 그대로 띄운다.

```json
{ "status": 404, "code": "CARD_NOT_FOUND", "message": "시나리오 카드를 찾을 수 없습니다." }
```

| `code` | 상태 | 언제 |
|---|---|---|
| `INVALID_REQUEST` | 400 | 경로 변수 타입 불일치, 본문 검증 실패 |
| `NOT_FOUND` | 404 | 없는 경로 |
| `CARD_NOT_FOUND` | 404 | 없는 카드 |
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

이후 슬라이스에서 쓸 이름도 같은 규칙을 따른다 — `훈련 세션` `TrainingSession`, `발화 턴` `UtteranceTurn`,
`태도 3축` `AttitudeScore`(정서 안정 `emotionalStability` · 공감 인정 `empathyAcknowledgement` · 경계 설정 `boundarySetting`),
`진정 종료` `CALMED`, `소요 턴 수` `turnsToCalm`, `성장 기록` `GrowthRecord`, `음성 서비스` `VoiceService`,
`추론 서비스` `InferenceService`, `출력 검사` `OutputCheck`.

## 패키지 구조

```
com.jinsangstop
├── common
│   ├── config      CORS · OpenAPI
│   └── error       ErrorCode · ErrorResponse · 전역 예외 처리
├── card            시나리오 카드 (domain · dto · service · controller)
└── intensity       강도 (domain · dto · controller)
```
