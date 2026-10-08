-- M22.4 completion: provenance for every application state change.
-- actor_type: USER (candidate action), SYSTEM (CareerPilot automation), COMMUNICATION (HR evidence).
-- source_communication_id: the HR communication whose evidence caused the change, when applicable.

ALTER TABLE application_state_history ADD COLUMN IF NOT EXISTS actor_type VARCHAR(20);
ALTER TABLE application_state_history ADD COLUMN IF NOT EXISTS source_communication_id UUID;

UPDATE application_state_history SET actor_type = CASE WHEN actor_id IS NULL THEN 'SYSTEM' ELSE 'USER' END
WHERE actor_type IS NULL;

CREATE INDEX IF NOT EXISTS idx_state_history_app_created ON application_state_history(application_id, created_at);
