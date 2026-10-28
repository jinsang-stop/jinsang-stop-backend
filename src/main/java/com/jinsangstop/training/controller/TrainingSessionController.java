package com.jinsangstop.training.controller;

import com.jinsangstop.auth.security.LoginUser;
import com.jinsangstop.common.config.OpenApiConfig;
import com.jinsangstop.common.error.BusinessException;
import com.jinsangstop.common.error.ErrorCode;
import com.jinsangstop.common.error.ErrorExamples;
import com.jinsangstop.common.error.ErrorResponse;
import com.jinsangstop.training.dto.StartTrainingSessionRequest;
import com.jinsangstop.training.dto.TrainingSessionResponse;
import com.jinsangstop.training.dto.TurnScoreResponse;
import com.jinsangstop.training.dto.UtteranceTurnResponse;
import com.jinsangstop.training.service.TrainingSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;

/**
 * 훈련 세션 시작, 발화 턴 제출, 턴 점수 조회 API. 로그인이 필요하다.
 */
@Tag(name = OpenApiConfig.TAG_TRAINING)
@RestController
@RequestMapping(value = "/api/training-sessions", produces = MediaType.APPLICATION_JSON_VALUE)
public class TrainingSessionController {

	/** Swagger 예시 — 시작된 세션 */
	public static final String SESSION_EXAMPLE = """
			{
			  "sessionId": 1,
			  "cardId": 1,
			  "complainantRoleName": "손님",
			  "intensity": 1,
			  "hardTurnLimit": 12,
			  "status": "IN_PROGRESS",
			  "turnCount": 0,
			  "startedAt": "2026-10-28T14:00:00"
			}""";

	/** Swagger 예시 — 확정된 턴 */
	public static final String TURN_EXAMPLE = """
			{
			  "sessionId": 1,
			  "turnNumber": 1,
			  "userTranscript": "죄송합니다 손님",
			  "complainantLine": "죄송하면 다예요? 지금 바로 환불해 달라고요.",
			  "calmed": false,
			  "complainantAudio": "UklGRiQAAABXQVZFZm10IBAAAAABAAEARKwAAIhYAQACABAAZGF0YQAAAAA=",
			  "complainantAudioContentType": "audio/wav",
			  "score": {
			    "turnNumber": 1,
			    "status": "SCORED",
			    "emotionalStability": { "score": 4, "reason": "도발에도 차분한 존댓말을 유지했다." },
			    "empathyAcknowledgement": { "score": 3, "reason": "짧은 사과로 불편을 인정했다." },
			    "boundarySetting": { "score": 3, "reason": "거절할 요구가 아직 없는 인사였다." }
			  }
			}""";

	/** Swagger 예시 — 턴 점수(채점됨) */
	public static final String SCORE_EXAMPLE = """
			{
			  "turnNumber": 1,
			  "status": "SCORED",
			  "emotionalStability": { "score": 4, "reason": "도발에도 차분한 존댓말을 유지했다." },
			  "empathyAcknowledgement": { "score": 3, "reason": "짧은 사과로 불편을 인정했다." },
			  "boundarySetting": { "score": 3, "reason": "거절할 요구가 아직 없는 인사였다." }
			}""";

	/** Swagger 예시 — 턴 점수(채점 실패 = 정보 없음) */
	public static final String SCORE_FAILED_EXAMPLE = """
			{ "turnNumber": 2, "status": "FAILED", "emotionalStability": null, "empathyAcknowledgement": null, "boundarySetting": null }""";

	private final TrainingSessionService trainingSessionService;
	/** 받을 수 있는 오디오 최대 크기(바이트) — 음성 서비스 상한과 맞춘다 */
	private final long maxAudioBytes;

	public TrainingSessionController(TrainingSessionService trainingSessionService,
			@Value("${jinsangstop.training.max-audio-bytes}") long maxAudioBytes) {
		this.trainingSessionService = trainingSessionService;
		this.maxAudioBytes = maxAudioBytes;
	}

	/** 훈련 세션 시작 */
	@Operation(summary = "훈련 세션 시작",
			description = """
					고른 카드와 강도로 훈련 세션을 연다.

					- 시연 장비 한 대가 **한 번에 한 세션만** 돌린다. 누군가(본인 포함) 훈련 중이면 `409 TRAINING_SESSION_BUSY` — "잠시 후 다시" 안내를 띄운다.
					  마지막 턴 뒤로 오래(기본 10분) 아무 활동이 없던 세션은 방치된 것으로 보고 정리한 뒤 새 세션을 연다.
					- 음성 서비스나 추론 서비스가 꺼져 있으면 세션을 만들지 않고 `503 TRAINING_ENVIRONMENT_NOT_READY`.
					- 2·3단계 강도는 프론트가 고지 확인을 받은 뒤에 보낸다.""",
			security = @SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE_SCHEME))
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "세션 시작",
					content = @Content(schema = @Schema(implementation = TrainingSessionResponse.class),
							examples = @ExampleObject(name = "시작된 세션", value = SESSION_EXAMPLE))),
			@ApiResponse(responseCode = "400", description = "카드 누락·강도 범위 밖(INVALID_REQUEST)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "INVALID_REQUEST", value = ErrorExamples.INVALID_REQUEST))),
			@ApiResponse(responseCode = "401", description = "로그인 필요(UNAUTHORIZED)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "UNAUTHORIZED", value = ErrorExamples.UNAUTHORIZED))),
			@ApiResponse(responseCode = "404", description = "없는 카드(CARD_NOT_FOUND)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "CARD_NOT_FOUND", value = ErrorExamples.CARD_NOT_FOUND))),
			@ApiResponse(responseCode = "409", description = "다른 훈련 진행 중(TRAINING_SESSION_BUSY) — 대기 안내",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "TRAINING_SESSION_BUSY", value = ErrorExamples.TRAINING_SESSION_BUSY))),
			@ApiResponse(responseCode = "503", description = "음성·추론 서비스 미준비(TRAINING_ENVIRONMENT_NOT_READY)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "TRAINING_ENVIRONMENT_NOT_READY", value = ErrorExamples.TRAINING_ENVIRONMENT_NOT_READY)))
	})
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public TrainingSessionResponse start(@AuthenticationPrincipal LoginUser loginUser,
			@Valid @RequestBody StartTrainingSessionRequest request) {
		return trainingSessionService.start(loginUser.getUserId(), request.cardId(), request.intensityOrDefault());
	}

	/** 발화 턴 제출 */
	@Operation(summary = "발화 턴 제출",
			description = """
					"말하기 끝내기"를 누르면 녹음한 오디오를 **요청 본문에 그대로**(multipart 아님) 보낸다.
					```js
					fetch(`/api/training-sessions/${sessionId}/turns`, {
					  method: 'POST', credentials: 'include',
					  headers: { 'Content-Type': blob.type },   // 예: audio/webm;codecs=opus
					  body: blob,                               // MediaRecorder가 만든 Blob
					})
					```
					응답이 올 때까지(전사 → 민원인 대사 생성 → 음성 합성) 수 초가 걸리므로 대기 표시를 띄운다.

					**응답 처리**
					- `complainantAudio`(Base64 WAV)를 디코딩해 바로 재생하고, `complainantLine`을 역할명과 함께 표시한다.
					- `score`로 태도 3축을 갱신한다. `status`가 `PENDING`이면 채점이 아직 안 끝난 것이므로 점수 조회 API를 잠시 뒤(예: 1초 간격) 다시 부른다.
					  `FAILED`면 세 축 모두 "채점되지 않음" — 마지막 성공값을 유지하고 표시만 한다. 세 축을 합산하지 않는다.
					  ```js
					  const audio = new Audio(`data:${res.complainantAudioContentType};base64,${res.complainantAudio}`);
					  audio.play();
					  ```
					- 전사가 비었으면 `422 EMPTY_TRANSCRIPT` — **턴 번호를 소모하지 않으니** "다시 말해 주세요"를 띄우고 다시 녹음하면 된다.
					- 오디오를 읽지 못하면 `422 AUDIO_UNREADABLE` — 역시 다시 녹음.
					- 음성·추론 서비스 문제는 `503 TRAINING_ENVIRONMENT_NOT_READY` — 턴은 소모되지 않는다. 같은 녹음으로 다시 시도할 수 있다.
					- `500 SESSION_ENDED_BY_SYSTEM_ERROR` — 민원인 대사가 안전 검사를 통과하지 못해 **세션이 끝났다.** 안내 화면을 띄우고 새 훈련으로 보낸다.
					- 오디오는 전사 직후 버리고 서버 어디에도 저장하지 않는다. 합성 음성도 저장하지 않으므로 다시 받을 수 없다.""",
			security = @SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE_SCHEME),
			requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
					description = "녹음 오디오 바이트(webm/opus · ogg/opus · wav · mp3). Content-Type은 audio/*",
					content = @Content(mediaType = "audio/webm", schema = @Schema(type = "string", format = "binary"))))
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "턴 확정",
					content = @Content(schema = @Schema(implementation = UtteranceTurnResponse.class),
							examples = @ExampleObject(name = "확정된 턴", value = TURN_EXAMPLE))),
			@ApiResponse(responseCode = "401", description = "로그인 필요(UNAUTHORIZED)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "UNAUTHORIZED", value = ErrorExamples.UNAUTHORIZED))),
			@ApiResponse(responseCode = "404", description = "없거나 남의 세션(TRAINING_SESSION_NOT_FOUND)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "TRAINING_SESSION_NOT_FOUND", value = ErrorExamples.TRAINING_SESSION_NOT_FOUND))),
			@ApiResponse(responseCode = "409", description = "이미 끝난 세션(TRAINING_SESSION_NOT_IN_PROGRESS)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "TRAINING_SESSION_NOT_IN_PROGRESS", value = ErrorExamples.TRAINING_SESSION_NOT_IN_PROGRESS))),
			@ApiResponse(responseCode = "413", description = "오디오 크기 초과(AUDIO_TOO_LARGE)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "AUDIO_TOO_LARGE", value = ErrorExamples.AUDIO_TOO_LARGE))),
			@ApiResponse(responseCode = "415", description = "Content-Type이 audio/*가 아님(UNSUPPORTED_MEDIA_TYPE)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "UNSUPPORTED_MEDIA_TYPE", value = ErrorExamples.UNSUPPORTED_MEDIA_TYPE))),
			@ApiResponse(responseCode = "422", description = "빈 전사(EMPTY_TRANSCRIPT) 또는 오디오 해독 실패(AUDIO_UNREADABLE) — 턴 미소모, 다시 녹음",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = {
									@ExampleObject(name = "EMPTY_TRANSCRIPT", value = ErrorExamples.EMPTY_TRANSCRIPT),
									@ExampleObject(name = "AUDIO_UNREADABLE", value = ErrorExamples.AUDIO_UNREADABLE)
							})),
			@ApiResponse(responseCode = "500", description = "출력 검사 재시도 실패로 세션 종료(SESSION_ENDED_BY_SYSTEM_ERROR)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "SESSION_ENDED_BY_SYSTEM_ERROR", value = ErrorExamples.SESSION_ENDED_BY_SYSTEM_ERROR))),
			@ApiResponse(responseCode = "503", description = "음성·추론 서비스 문제(TRAINING_ENVIRONMENT_NOT_READY) — 턴 미소모",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "TRAINING_ENVIRONMENT_NOT_READY", value = ErrorExamples.TRAINING_ENVIRONMENT_NOT_READY)))
	})
	@PostMapping(value = "/{sessionId}/turns", consumes = "audio/*")
	@ResponseStatus(HttpStatus.CREATED)
	public UtteranceTurnResponse submitTurn(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "세션 식별자 — 세션 시작 응답의 sessionId", example = "1") @PathVariable Long sessionId,
			HttpServletRequest request) throws IOException {
		byte[] audio = readAudio(request);
		return trainingSessionService.submitTurn(loginUser.getUserId(), sessionId, audio, request.getContentType());
	}

	/** 턴 점수 조회 */
	@Operation(summary = "턴 점수 조회",
			description = """
					발화 턴의 태도 3축 점수를 돌려준다. 채점은 턴 응답보다 늦게 끝날 수 있다 —
					턴 제출 응답의 `score.status`가 `PENDING`이면 이 API를 잠시 뒤 다시 불러 갱신한다.

					- `SCORED`: 세 축 모두 1~5 점수와 짧은 근거
					- `FAILED`: 채점 실패 — 세 축 모두 `null`(**0점이 아니라 정보 없음**)
					- 세 축을 합산한 종합 점수는 없다.""",
			security = @SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE_SCHEME))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공",
					content = @Content(schema = @Schema(implementation = TurnScoreResponse.class),
							examples = {
									@ExampleObject(name = "채점됨", value = SCORE_EXAMPLE),
									@ExampleObject(name = "정보 없음", value = SCORE_FAILED_EXAMPLE)
							})),
			@ApiResponse(responseCode = "401", description = "로그인 필요(UNAUTHORIZED)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "UNAUTHORIZED", value = ErrorExamples.UNAUTHORIZED))),
			@ApiResponse(responseCode = "404", description = "없거나 남의 세션(TRAINING_SESSION_NOT_FOUND) · 없는 턴(TURN_NOT_FOUND)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = {
									@ExampleObject(name = "TRAINING_SESSION_NOT_FOUND", value = ErrorExamples.TRAINING_SESSION_NOT_FOUND),
									@ExampleObject(name = "TURN_NOT_FOUND", value = ErrorExamples.TURN_NOT_FOUND)
							}))
	})
	@GetMapping("/{sessionId}/turns/{turnNumber}/score")
	public TurnScoreResponse getTurnScore(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "세션 식별자", example = "1") @PathVariable Long sessionId,
			@Parameter(description = "턴 번호", example = "1") @PathVariable int turnNumber) {
		return trainingSessionService.getTurnScore(loginUser.getUserId(), sessionId, turnNumber);
	}

	/**
	 * 요청 본문을 메모리로만 읽는다(디스크 임시 파일 없음, ADR-0006).
	 * 상한을 넘으면 끝까지 읽지 않고 거절한다 — 큰 본문으로 메모리를 고갈시키지 못하게 한다.
	 */
	private byte[] readAudio(HttpServletRequest request) throws IOException {
		long declaredLength = request.getContentLengthLong();
		if (declaredLength > maxAudioBytes) {
			throw new BusinessException(ErrorCode.AUDIO_TOO_LARGE);
		}
		try (InputStream in = request.getInputStream()) {
			byte[] audio = in.readNBytes((int) maxAudioBytes + 1);
			if (audio.length > maxAudioBytes) {
				throw new BusinessException(ErrorCode.AUDIO_TOO_LARGE);
			}
			return audio;
		}
	}
}
