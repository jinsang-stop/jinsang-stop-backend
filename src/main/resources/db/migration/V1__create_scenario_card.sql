-- 시나리오 카드 테이블 (ADR-0003: 카드는 사람이 쓰고, 애플리케이션은 읽기만 한다)
-- calming_condition(진정 조건)과 non_negotiable_line(양보 불가선)은 DB에만 두고 프론트 응답에 싣지 않는다.
CREATE TABLE scenario_card (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    card_type             VARCHAR(20)  NOT NULL,              -- OFFICIAL(공식 카드). 내 카드는 MVP 제외(ADR-0007)
    name                  VARCHAR(100) NOT NULL,              -- 카드 이름
    situation             TEXT         NOT NULL,              -- 상황 설정
    complainant_role_name VARCHAR(30)  NOT NULL,              -- 민원인 화면 표시 역할명(손님 등)
    complainant_persona   TEXT         NOT NULL,              -- 민원인 페르소나
    complainant_demand    TEXT         NOT NULL,              -- 민원인의 실제 요구
    non_negotiable_line   TEXT         NOT NULL,              -- 양보하면 안 되는 선
    calming_condition     TEXT         NOT NULL,              -- 진정 조건
    hard_turn_limit       INT          NOT NULL,              -- 하드 턴 상한
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_scenario_card_type ON scenario_card (card_type);
