package com.jinsangstop.growth;

import com.jinsangstop.auth.security.LoginUser;
import com.jinsangstop.common.config.OpenApiConfig;
import com.jinsangstop.common.error.ErrorExamples;
import com.jinsangstop.common.error.ErrorResponse;
import com.jinsangstop.growth.dto.GrowthRecordResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 성장 기록 조회 API. 로그인이 필요하다.
 */
@Tag(name = OpenApiConfig.TAG_GROWTH)
@RestController
public class GrowthRecordController {

	/** Swagger 예시 */
	public static final String GROWTH_EXAMPLE = """
			{
			  "cardId": 1,
			  "cardName": "영수증 없는 도시락 환불",
			  "intensity": 1,
			  "boundaryThresholdConfigured": true,
			  "points": [
			    { "sessionId": 3, "endedAt": "2026-10-21T15:00:00", "turnsToCalm": 7, "emotionalStability": 3.57, "empathyAcknowledgement": 3.0, "boundarySetting": 3.86 },
			    { "sessionId": 9, "endedAt": "2026-10-28T15:00:00", "turnsToCalm": 4, "emotionalStability": 4.25, "empathyAcknowledgement": 4.0, "boundarySetting": 4.0 }
			  ]
			}""";

	private final GrowthRecordService growthRecordService;

	public GrowthRecordController(GrowthRecordService growthRecordService) {
		this.growthRecordService = growthRecordService;
	}

	/** 성장 기록 조회 */
	@Operation(summary = "성장 기록 조회",
			description = """
					같은 카드·같은 강도로 **진정 종료에 도달하고 편입 조건을 만족한** 내 세션들의 소요 턴 수 시계열.

					- 주지표는 `turnsToCalm`(작을수록 성장), 세 축 평균은 보조 지표다. 합산 점수는 없다.
					- 강도마다 따로 부른다 — 강도가 다른 점을 한 선으로 잇지 않는다.
					- 편입 조건: 진정 종료 AND 모든 턴 채점됨 AND 경계 설정 평균 ≥ 하한선. 미편입 사유는 리포트에서 본다.
					- `boundaryThresholdConfigured`가 false면 하한선이 아직 정해지지 않아 모든 세션이 편입 보류다(점이 없다).""",
			security = @SecurityRequirement(name = OpenApiConfig.SESSION_COOKIE_SCHEME))
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공(점이 없으면 빈 배열)",
					content = @Content(schema = @Schema(implementation = GrowthRecordResponse.class),
							examples = @ExampleObject(name = "점 두 개", value = GROWTH_EXAMPLE))),
			@ApiResponse(responseCode = "400", description = "카드·강도 누락 또는 강도 범위 밖(INVALID_REQUEST)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "INVALID_REQUEST", value = ErrorExamples.INVALID_REQUEST))),
			@ApiResponse(responseCode = "401", description = "로그인 필요(UNAUTHORIZED)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "UNAUTHORIZED", value = ErrorExamples.UNAUTHORIZED))),
			@ApiResponse(responseCode = "404", description = "없는 카드(CARD_NOT_FOUND)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "CARD_NOT_FOUND", value = ErrorExamples.CARD_NOT_FOUND)))
	})
	@GetMapping(value = "/api/growth-records", produces = MediaType.APPLICATION_JSON_VALUE)
	public GrowthRecordResponse getGrowthRecord(@AuthenticationPrincipal LoginUser loginUser,
			@Parameter(description = "카드 식별자", example = "1", required = true) @RequestParam Long cardId,
			@Parameter(description = "강도(1~3)", example = "1", required = true)
			@RequestParam @Min(1) @Max(3) int intensity) {
		return growthRecordService.getGrowthRecord(loginUser.getUserId(), cardId, intensity);
	}
}
