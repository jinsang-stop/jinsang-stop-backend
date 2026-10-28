package com.jinsangstop.card.dto;

import com.jinsangstop.card.domain.ScenarioCard;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 프론트엔드에 내려가는 공식 카드 요약(목록·상세 공통).
 * 진정 조건과 양보 불가선은 필드 자체를 두지 않는다 — 사용자가 정답을 보고 연기하지 않게 하기 위함이다.
 */
@Schema(description = "공식 카드 요약 — 목록과 상세가 같은 형식이다. 진정 조건·양보 불가선은 포함하지 않는다")
public record ScenarioCardSummaryResponse(
		@Schema(description = "카드 식별자. 상세 조회와 이후 훈련 세션 시작 요청에 그대로 쓴다",
				example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
		Long cardId,
		@Schema(description = "카드 이름 — 목록의 카드 제목", example = "영수증 없는 도시락 환불",
				requiredMode = Schema.RequiredMode.REQUIRED)
		String name,
		@Schema(description = "상황 설정 — 카드 상세에서 훈련 전에 읽히는 상황 설명",
				example = "편의점 아침 시간. 손님이 어제 산 도시락이 상했다며 영수증 없이 계산대로 와서 현금 환불을 요구한다. 결제 수단은 기억나지 않는다고 한다.",
				requiredMode = Schema.RequiredMode.REQUIRED)
		String situation,
		@Schema(description = "민원인을 화면에 표시할 역할명 — 대화 화면의 상대 이름표로 쓴다",
				example = "손님", requiredMode = Schema.RequiredMode.REQUIRED)
		String complainantRoleName,
		@Schema(description = "하드 턴 상한 — 이 발화 턴 수에 닿으면 진정시키지 못한 채 종료된다. 진행 표시(예: 3/12)에 쓸 수 있다",
				example = "12", minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED)
		int hardTurnLimit
) {

	/** 엔티티에서 프론트 공개 필드만 골라 만든다. */
	public static ScenarioCardSummaryResponse from(ScenarioCard card) {
		return new ScenarioCardSummaryResponse(
				card.getId(),
				card.getName(),
				card.getSituation(),
				card.getComplainantRoleName(),
				card.getHardTurnLimit());
	}
}
