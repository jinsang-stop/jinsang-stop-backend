package com.jinsangstop.report.controller;

import com.jinsangstop.auth.security.LoginUser;
import com.jinsangstop.common.config.OpenApiConfig;
import com.jinsangstop.common.error.ErrorExamples;
import com.jinsangstop.common.error.ErrorResponse;
import com.jinsangstop.report.dto.SessionReportResponse;
import com.jinsangstop.report.service.SessionReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 세션 리포트 API. 로그인이 필요하다.
 */
@Tag(name = OpenApiConfig.TAG_REPORT)
@RestController
public class SessionReportController {

	/** Swagger 예시 — 상한 도달로 끝나 미편입된 세션 */
	public static final String REPORT_EXAMPLE = """
			{
			  "sessionId": 1,
			  "cardId": 1,
			  "cardName": "영수증 없는 도시락 환불",
			  "complainantRoleName": "손님",
			  "intensity": 1,
			  "endReason": "TURN_LIMIT_REACHED",
			  "turnsToCalm": null,
			  "startedAt": "2026-10-28T14:00:00",
			  "endedAt": "2026-10-28T14:09:12",
			  "turns": [
			    {
			      "turnNumber": 1,
			      "userTranscript": "어서 오세요, 무엇을 도와드릴까요?",
			      "complainantLine": "어제 산 도시락이 상했어요. 당장 현금으로 환불해 주세요.",
			      "calmed": false,
			      "score": {
			        "turnNumber": 1,
			        "status": "SCORED",
			        "emotionalStability": { "score": 4, "reason": "차분한 말투로 응대를 시작했다." },
			        "empathyAcknowledgement": { "score": 3, "reason": "인사만 있고 아직 감정을 받아줄 단계가 아니었다." },
			        "boundarySetting": { "score": 3, "reason": "거절할 요구가 아직 없었다." }
			      }
			    },
			    {
			      "turnNumber": 2,
			      "userTranscript": "아 네… 그럼 그냥 환불해 드릴게요.",
			      "complainantLine": "진작 그럴 것이지.",
			      "calmed": false,
			      "score": { "turnNumber": 2, "status": "FAILED", "emotionalStability": null, "empathyAcknowledgement": null, "boundarySetting": null }
			    }
			  ],
			  "axisAverages": { "emotionalStability": 4.0, "empathyAcknowledgement": 3.0, "boundarySetting": 3.0 },
			  "growthRecord": {
			    "included": false,
			    "reasons": [
			      { "code": "NOT_CALMED", "message": "진정 종료로 끝나지 않은 훈련은 성장 기록에 들어가지 않아요." },
			      { "code": "UNSCORED_TURNS", "message": "채점되지 않은 턴이 있어 성장 기록에 넣지 않았어요." }
			    ]
			  }
			}""";

	private final SessionReportService sessionReportService;

	public SessionReportController(SessionReportService sessionReportService) {
		this.sessionReportService = sessionReportService;
	}

	/** 세션 리포트 조회 */
	@Operation(summary = "세션 리포트 조회",
			description = """
					끝난 세션의 리포트 — 턴 타임라인(전사·민원인 대사·축별 점수/정보 없음), 종료 사유, 소요 턴 수(진정 종료일 때만),
					축별 평균, 성장 기록 편입 여부와 미편입 사유.

					- 미편입 사유는 여러 개가 함께 붙을 수 있다. `message`를 그대로 보여 준다.
					- 진정 조건·양보 불가선·"올바른 조치" 항목은 없다. 세 축을 합산한 점수도 없다.
					- 진행 중인 세션은 `409 TRAINING_SESSION_IN_PROGRESS`.""",
			security = @SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE_SCHEME))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공",
					content = @Content(schema = @Schema(implementation = SessionReportResponse.class),
							examples = @ExampleObject(name = "상한 도달 · 미편입", value = REPORT_EXAMPLE))),
			@ApiResponse(responseCode = "401", description = "로그인 필요(UNAUTHORIZED)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "UNAUTHORIZED", value = ErrorExamples.UNAUTHORIZED))),
			@ApiResponse(responseCode = "404", description = "없거나 남의 세션(TRAINING_SESSION_NOT_FOUND)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "TRAINING_SESSION_NOT_FOUND", value = ErrorExamples.TRAINING_SESSION_NOT_FOUND))),
			@ApiResponse(responseCode = "409", description = "아직 진행 중(TRAINING_SESSION_IN_PROGRESS)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "TRAINING_SESSION_IN_PROGRESS", value = ErrorExamples.TRAINING_SESSION_IN_PROGRESS)))
	})
	@GetMapping(value = "/api/training-sessions/{sessionId}/report", produces = MediaType.APPLICATION_JSON_VALUE)
	public SessionReportResponse getReport(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "세션 식별자", example = "1") @PathVariable Long sessionId) {
		return sessionReportService.getReport(loginUser.getUserId(), sessionId);
	}
}
