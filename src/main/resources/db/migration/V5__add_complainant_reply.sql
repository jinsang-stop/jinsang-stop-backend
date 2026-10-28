-- 민원인 응답 (backend #4, ADR-0009)
-- acting_seed: 연기 호출 시드 — 세션마다 기록해 같은 대화를 재현할 수 있게 한다
ALTER TABLE training_session ADD COLUMN acting_seed BIGINT NULL;

-- 발화 턴에 민원인 대사(출력 검사 통과본)와 진정 여부를 붙인다. 합성 음성은 저장하지 않는다(ADR-0006).
ALTER TABLE utterance_turn ADD COLUMN complainant_line TEXT NULL;
ALTER TABLE utterance_turn ADD COLUMN calmed BOOLEAN NOT NULL DEFAULT FALSE;
