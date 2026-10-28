package com.jinsangstop.intensity.controller;

import com.jinsangstop.common.config.OpenApiConfig;
import com.jinsangstop.common.error.ErrorExamples;
import com.jinsangstop.common.error.ErrorResponse;
import com.jinsangstop.intensity.domain.Intensity;
import com.jinsangstop.intensity.dto.IntensityGuideResponse;
import com.jinsangstop.intensity.dto.IntensityLevelResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

/**
 * 강도 단계별 고지 문구 API. 프론트엔드는 이 응답으로 강도 선택·고지 확인 화면을 그린다.
 */
@Tag(name = OpenApiConfig.TAG_INTENSITY)
@RestController
@RequestMapping(value = "/api/intensities", produces = MediaType.APPLICATION_JSON_VALUE)
public class IntensityController {

	/** 모든 단계 공통 안내 — 절대선(ADR-0005)과 즉시 중단 가능 여부 */
	static final String COMMON_NOTICE =
			"어느 단계에서도 성적 발언, 신체적 위협 묘사, 특정 집단에 대한 혐오 표현은 나오지 않습니다. "
					+ "훈련 중 언제든 바로 중단할 수 있습니다.";

	/** Swagger 예시 — 실제 응답과 같은지 OpenApiExampleTest가 검사한다 */
	public static final String GUIDE_EXAMPLE = """
			{
			  "defaultLevel": 1,
			  "commonNotice": "어느 단계에서도 성적 발언, 신체적 위협 묘사, 특정 집단에 대한 혐오 표현은 나오지 않습니다. 훈련 중 언제든 바로 중단할 수 있습니다.",
			  "levels": [
			    {
			      "level": 1,
			      "title": "1단계 · 불만 표현",
			      "allowedBehaviors": ["불만과 요구를 분명하게 말한다", "같은 요구를 여러 번 되풀이한다", "존댓말과 평소 목소리 크기를 유지한다"],
			      "requiresConsent": false
			    },
			    {
			      "level": 2,
			      "title": "2단계 · 언성과 비꼬기",
			      "allowedBehaviors": ["1단계의 행동을 모두 한다", "언성을 높이고 반말을 섞는다", "직원의 말을 비꼬거나 무시하는 투로 말한다", "점장이나 책임자를 불러 달라고 요구한다"],
			      "requiresConsent": true
			    },
			    {
			      "level": 3,
			      "title": "3단계 · 고성과 비하",
			      "allowedBehaviors": ["2단계의 행동을 모두 한다", "고성으로 말을 몰아붙인다", "직원의 업무 능력과 태도를 깎아내리는 말을 한다", "온라인 리뷰나 본사 민원으로 문제 삼겠다고 압박한다"],
			      "requiresConsent": true
			    }
			  ]
			}""";

	/** 강도 고지 정보 조회 */
	@Operation(summary = "강도 고지 정보 조회",
			description = """
					강도 선택 화면과 고지 확인 화면에 필요한 값을 한 번에 돌려준다.

					**화면 흐름**
					1. 초기 선택값은 `defaultLevel`
					2. 사용자가 단계를 고르면 해당 `levels[]` 항목의 `requiresConsent`를 본다
					3. `true`면 `title`·`allowedBehaviors`·`commonNotice`를 보여 주고 확인 버튼을 눌러야 다음으로 넘어간다
					4. `false`(1단계)면 바로 넘어간다

					값이 바뀌는 일은 드물어 화면 진입 때 한 번 받아 두면 된다.""")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "조회 성공",
					content = @Content(schema = @Schema(implementation = IntensityGuideResponse.class),
							examples = @ExampleObject(name = "강도 3단계", value = GUIDE_EXAMPLE))),
			@ApiResponse(responseCode = "500", description = "서버 내부 오류(INTERNAL_ERROR)",
					content = @Content(schema = @Schema(implementation = ErrorResponse.class),
							examples = @ExampleObject(name = "INTERNAL_ERROR", value = ErrorExamples.INTERNAL_ERROR)))
	})
	@GetMapping
	public IntensityGuideResponse getIntensityGuide() {
		return new IntensityGuideResponse(
				Intensity.DEFAULT.level(),
				COMMON_NOTICE,
				Arrays.stream(Intensity.values()).map(IntensityLevelResponse::from).toList());
	}
}
