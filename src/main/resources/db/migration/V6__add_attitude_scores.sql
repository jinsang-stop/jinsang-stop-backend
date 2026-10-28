-- 태도 3축 점수 (backend #5, ADR-0002)
-- 축마다 1~5 정수 점수와 짧은 근거. 채점에 실패하면 점수는 NULL(정보 없음)이고 0으로 저장하지 않는다.
-- score_status: PENDING(채점 중) / SCORED(채점됨) / FAILED(정보 없음)
ALTER TABLE utterance_turn ADD COLUMN score_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE utterance_turn ADD COLUMN emotional_stability_score INT NULL;
ALTER TABLE utterance_turn ADD COLUMN emotional_stability_reason VARCHAR(200) NULL;
ALTER TABLE utterance_turn ADD COLUMN empathy_acknowledgement_score INT NULL;
ALTER TABLE utterance_turn ADD COLUMN empathy_acknowledgement_reason VARCHAR(200) NULL;
ALTER TABLE utterance_turn ADD COLUMN boundary_setting_score INT NULL;
ALTER TABLE utterance_turn ADD COLUMN boundary_setting_reason VARCHAR(200) NULL;
