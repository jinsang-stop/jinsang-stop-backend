package com.jinsangstop.intensity.dto;

import com.jinsangstop.intensity.domain.Intensity;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 강도 한 단계의 고지 정보.
 */
@Schema(description = "강도 한 단계의 고지 정보")
public record IntensityLevelResponse(
		@Schema(description = "강도 단계. 훈련 세션 시작 요청에 이 값을 보낸다", example = "2",
				allowableValues = {"1", "2", "3"}, requiredMode = Schema.RequiredMode.REQUIRED)
		int level,
		@Schema(description = "강도 선택 버튼·고지 화면 제목에 쓰는 단계 이름", example = "2단계 · 언성과 비꼬기",
				requiredMode = Schema.RequiredMode.REQUIRED)
		String title,
		@ArraySchema(arraySchema = @Schema(description = "이 단계에서 민원인이 할 수 있는 행동. 고지 화면에 목록으로 보여 준다. 여기 없는 행동은 하지 않는다",
				example = "[\"1단계의 행동을 모두 한다\", \"언성을 높이고 반말을 섞는다\"]"),
				schema = @Schema(example = "언성을 높이고 반말을 섞는다"))
		List<String> allowedBehaviors,
		@Schema(description = "true면 시작 전에 allowedBehaviors를 보여 주고 사용자 확인을 받아야 한다(2·3단계)",
				example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
		boolean requiresConsent
) {

	/** 강도 enum을 응답으로 바꾼다. */
	public static IntensityLevelResponse from(Intensity intensity) {
		return new IntensityLevelResponse(
				intensity.level(),
				intensity.title(),
				intensity.allowedBehaviors(),
				intensity.requiresConsent());
	}
}
