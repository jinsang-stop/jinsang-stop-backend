-- 종료 판정 (backend #6, ADR-0004)
-- turns_to_calm: 소요 턴 수 — 진정 종료(CALMED)일 때만 기록한다. 하드 턴 상한·중단·시스템 오류에는 NULL이다.
ALTER TABLE training_session ADD COLUMN turns_to_calm INT NULL;
