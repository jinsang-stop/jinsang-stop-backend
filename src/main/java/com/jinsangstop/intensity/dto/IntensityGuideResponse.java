package com.jinsangstop.intensity.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 강도 선택 화면에 필요한 전체 고지 정보 — 기본 단계, 모든 단계 공통 안내, 단계별 허용 행동.
 */
@Schema(description = "강도 선택 화면에 필요한 고지 정보 전체")
public record IntensityGuideResponse(
		@Schema(description = "강도 선택의 초기값. 프론트에 숫자를 박지 말고 이 값을 쓴다", example = "1",
				requiredMode = Schema.RequiredMode.REQUIRED)
		int defaultLevel,
		@Schema(description = "모든 단계에 공통으로 보여 줄 안내 — 강도 선택 화면 하단이나 고지 화면에 함께 표시",
				example = "어느 단계에서도 성적 발언, 신체적 위협 묘사, 특정 집단에 대한 혐오 표현은 나오지 않습니다. 훈련 중 언제든 바로 중단할 수 있습니다.",
				requiredMode = Schema.RequiredMode.REQUIRED)
		String commonNotice,
		@Schema(description = "단계별 고지 정보. 1단계부터 오름차순이다", requiredMode = Schema.RequiredMode.REQUIRED)
		List<IntensityLevelResponse> levels
) {
}
