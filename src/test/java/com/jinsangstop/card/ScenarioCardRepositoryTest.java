package com.jinsangstop.card;

import com.jinsangstop.card.domain.CardType;
import com.jinsangstop.card.domain.ScenarioCard;
import com.jinsangstop.card.domain.ScenarioCardRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시드된 공식 카드가 진정 조건·양보 불가선을 포함해 DB에 저장돼 있는지 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ScenarioCardRepositoryTest {

	@Autowired
	private ScenarioCardRepository scenarioCardRepository;

	@Test
	@DisplayName("공식 카드는 진정 조건과 양보 불가선을 DB에 갖고 있다")
	void hiddenFieldsAreStored() {
		List<ScenarioCard> cards = scenarioCardRepository.findAllByCardTypeOrderByIdAsc(CardType.OFFICIAL);

		assertThat(cards).isNotEmpty();
		assertThat(cards).allSatisfy(card -> {
			assertThat(card.getCalmingCondition()).isNotBlank();
			assertThat(card.getNonNegotiableLine()).isNotBlank();
			assertThat(card.getComplainantPersona()).isNotBlank();
			assertThat(card.getComplainantDemand()).isNotBlank();
			assertThat(card.getHardTurnLimit()).isPositive();
		});
	}
}
