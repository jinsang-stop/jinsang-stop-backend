package com.jinsangstop.card.controller;

import com.jinsangstop.card.dto.ScenarioCardSummaryResponse;
import com.jinsangstop.card.service.ScenarioCardService;
import com.jinsangstop.common.config.OpenApiConfig;
import com.jinsangstop.common.error.ErrorExamples;
import com.jinsangstop.common.error.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 공식 카드 목록·상세 조회 API.
 */
@Tag(name = OpenApiConfig.TAG_CARD)
@RestController
@RequestMapping(value = "/api/cards", produces = MediaType.APPLICATION_JSON_VALUE)
public class ScenarioCardController {

	/** Swagger 예시 — 목록 응답. 실제 응답과 같은지 OpenApiExampleTest가 검사한다 */
	public static final String LIST_EXAMPLE = """
			[
			  {
			    "cardId": 1,
			    "name": "영수증 없는 도시락 환불",
			    "situation": "편의점 아침 시간. 손님이 어제 산 도시락이 상했다며 영수증 없이 계산대로 와서 현금 환불을 요구한다. 결제 수단은 기억나지 않는다고 한다.",
			    "complainantRoleName": "손님",
			    "hardTurnLimit": 12
			  },
			  {
			    "cardId": 2,
			    "name": "주문 실수 음료 재제조와 보상 요구",
			    "situation": "카페 점심 피크 시간. 손님이 주문한 것과 다른 음료가 나왔다며 다시 만들어 달라고 하고, 다음 방문 때 쓸 무료 음료 쿠폰까지 요구한다.",
			    "complainantRoleName": "손님",
			    "hardTurnLimit": 10
			  }
			]""";

	/** Swagger 예시 — 상세 응답. 실제 응답과 같은지 OpenApiExampleTest가 검사한다 */
	public static final String DETAIL_EXAMPLE = """
			{
			  "cardId": 1,
			  "name": "영수증 없는 도시락 환불",
			  "situation": "편의점 아침 시간. 손님이 어제 산 도시락이 상했다며 영수증 없이 계산대로 와서 현금 환불을 요구한다. 결제 수단은 기억나지 않는다고 한다.",
			  "complainantRoleName": "손님",
			  "hardTurnLimit": 12
			}""";

	private final ScenarioCardService scenarioCardService;

	public ScenarioCardController(ScenarioCardService scenarioCardService) {
		this.scenarioCardService = scenarioCardService;
	}

	/** 공식 카드 목록 */
	@Operation(summary = "공식 카드 목록 조회",
			description = """
					카드 선택 화면에 띄울 공식 카드 전체를 `cardId` 오름차순으로 돌려준다.
					카드가 하나도 없으면 빈 배열 `[]`이다(오류 아님).
					진정 조건·양보 불가선은 포함하지 않는다.""")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공",
					content = @Content(
							array = @ArraySchema(schema = @Schema(implementation = ScenarioCardSummaryResponse.class)),
							examples = @ExampleObject(name = "공식 카드 2장", value = LIST_EXAMPLE))),
			@ApiResponse(responseCode = "500", description = "서버 내부 오류(INTERNAL_ERROR)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "INTERNAL_ERROR", value = ErrorExamples.INTERNAL_ERROR)))
	})
	@GetMapping
	public List<ScenarioCardSummaryResponse> getOfficialCards() {
		return scenarioCardService.getOfficialCards();
	}

	/** 공식 카드 상세 */
	@Operation(summary = "공식 카드 상세 조회",
			description = """
					카드 상세 화면용. 목록의 항목과 같은 형식이다.
					목록에서 이미 받은 값으로 화면을 그려도 되지만, 새로고침·직접 주소 진입 때는 이 API로 다시 받는다.""")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공",
					content = @Content(schema = @Schema(implementation = ScenarioCardSummaryResponse.class),
							examples = @ExampleObject(name = "공식 카드", value = DETAIL_EXAMPLE))),
			@ApiResponse(responseCode = "400", description = "카드 식별자가 숫자가 아님(INVALID_REQUEST)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "INVALID_REQUEST", value = ErrorExamples.INVALID_REQUEST))),
			@ApiResponse(responseCode = "404", description = "해당 카드 없음(CARD_NOT_FOUND) — 목록 화면으로 돌려보내면 된다",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "CARD_NOT_FOUND", value = ErrorExamples.CARD_NOT_FOUND))),
			@ApiResponse(responseCode = "500", description = "서버 내부 오류(INTERNAL_ERROR)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "INTERNAL_ERROR", value = ErrorExamples.INTERNAL_ERROR)))
	})
	@GetMapping("/{cardId}")
	public ScenarioCardSummaryResponse getOfficialCard(
			@Parameter(description = "카드 식별자 — 목록 응답의 `cardId`", example = "1") @PathVariable Long cardId) {
		return scenarioCardService.getOfficialCard(cardId);
	}
}
