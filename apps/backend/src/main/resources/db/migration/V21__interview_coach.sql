-- AI interview coach: sessions can target a discovered job (with or without an application) and
-- store real per-question evaluations instead of fixed scores.

ALTER TABLE interview_sessions ALTER COLUMN application_id DROP NOT NULL;
ALTER TABLE interview_sessions ADD COLUMN IF NOT EXISTS job_id UUID;
ALTER TABLE interview_sessions ADD COLUMN IF NOT EXISTS status VARCHAR(20);
ALTER TABLE interview_sessions ADD COLUMN IF NOT EXISTS difficulty VARCHAR(10);
ALTER TABLE interview_sessions ADD COLUMN IF NOT EXISTS target_role VARCHAR(255);
ALTER TABLE interview_sessions ADD COLUMN IF NOT EXISTS target_company VARCHAR(255);
ALTER TABLE interview_sessions ADD COLUMN IF NOT EXISTS summary_json TEXT;
ALTER TABLE interview_sessions ADD COLUMN IF NOT EXISTS ai_model VARCHAR(100);

ALTER TABLE interview_questions ADD COLUMN IF NOT EXISTS question_order INT;
ALTER TABLE interview_questions ADD COLUMN IF NOT EXISTS question_type VARCHAR(30);
ALTER TABLE interview_questions ADD COLUMN IF NOT EXISTS skill_area VARCHAR(255);
ALTER TABLE interview_questions ADD COLUMN IF NOT EXISTS difficulty VARCHAR(10);
ALTER TABLE interview_questions ADD COLUMN IF NOT EXISTS rationale TEXT;
ALTER TABLE interview_questions ADD COLUMN IF NOT EXISTS evaluation_criteria_json TEXT;
ALTER TABLE interview_questions ADD COLUMN IF NOT EXISTS provenance VARCHAR(60);
ALTER TABLE interview_questions ADD COLUMN IF NOT EXISTS relevance_score DOUBLE PRECISION;
ALTER TABLE interview_questions ADD COLUMN IF NOT EXISTS clarity_score DOUBLE PRECISION;
ALTER TABLE interview_questions ADD COLUMN IF NOT EXISTS overall_score DOUBLE PRECISION;
ALTER TABLE interview_questions ADD COLUMN IF NOT EXISTS evaluation_json TEXT;
ALTER TABLE interview_questions ADD COLUMN IF NOT EXISTS answered_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_interview_sessions_candidate ON interview_sessions(candidate_id, started_at);
