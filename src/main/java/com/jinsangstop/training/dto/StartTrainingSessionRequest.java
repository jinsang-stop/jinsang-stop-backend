package com.jinsangstop.training.dto;

import com.jinsangstop.intensity.domain.Intensity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 훈련 세션 시작 요청.
 */
@Schema(description = "훈련 세션 시작 요청")
public record StartTrainingSessionRequest(
		@Schema(description = "고른 공식 카드 식별자 — 카드 응답의 cardId", example = "1",
				requiredMode = Schema.RequiredMode.REQUIRED)
		@NotNull(message = "카드를 골라 주세요.")
		Long cardId,

		@Schema(description = "강도(1~3). 생략하면 기본값 1. 2·3단계는 프론트에서 고지 확인을 받은 뒤 보낸다",
				example = "1", allowableValues = {"1", "2", "3"})
		@Min(value = 1, message = "강도는 1~3 사이여야 합니다.")
		@Max(value = 3, message = "강도는 1~3 사이여야 합니다.")
		Integer intensity
) {

	/** 강도 값 — 생략했으면 기본 강도 */
	public int intensityOrDefault() {
		return intensity == null ? Intensity.DEFAULT.level() : intensity;
	}
}
