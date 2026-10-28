-- 훈련 세션과 발화 턴 (ADR-0001: 턴은 사용자가 닫는다 / ADR-0006: 오디오는 저장하지 않고 전사 텍스트만 남긴다)
CREATE TABLE training_session (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    user_id          BIGINT      NOT NULL,              -- 세션을 연 사용자
    card_id          BIGINT      NOT NULL,              -- 고른 시나리오 카드
    intensity        INT         NOT NULL,              -- 강도(1~3)
    status           VARCHAR(20) NOT NULL,              -- IN_PROGRESS / ABANDONED (종료 사유는 #6에서 추가)
    active_slot      INT         NULL,                  -- 진행 중이면 1, 끝나면 NULL — 유니크 제약으로 진행 중 세션을 하나로 묶는다
    turn_count       INT         NOT NULL DEFAULT 0,    -- 확정된 발화 턴 수(빈 전사는 세지 않는다)
    started_at       TIMESTAMP   NOT NULL,
    last_activity_at TIMESTAMP   NOT NULL,              -- 마지막 턴 제출 시각 — 오래 방치된 세션 정리에 쓴다
    ended_at         TIMESTAMP   NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_training_session_active_slot UNIQUE (active_slot),
    CONSTRAINT fk_training_session_user FOREIGN KEY (user_id) REFERENCES user_account (id),
    CONSTRAINT fk_training_session_card FOREIGN KEY (card_id) REFERENCES scenario_card (id)
);

CREATE INDEX idx_training_session_user ON training_session (user_id);

-- 발화 턴 — 지금은 사용자 전사만 담는다. 민원인 응답(#4)과 채점(#5)이 이 행에 붙는다.
CREATE TABLE utterance_turn (
    id               BIGINT    NOT NULL AUTO_INCREMENT,
    session_id       BIGINT    NOT NULL,
    turn_number      INT       NOT NULL,                -- 1부터 시작. 빈 전사는 번호를 소모하지 않는다
    user_transcript  TEXT      NOT NULL,                -- 사용자 발화 전사
    created_at       TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_utterance_turn_number UNIQUE (session_id, turn_number),
    CONSTRAINT fk_utterance_turn_session FOREIGN KEY (session_id) REFERENCES training_session (id)
);
