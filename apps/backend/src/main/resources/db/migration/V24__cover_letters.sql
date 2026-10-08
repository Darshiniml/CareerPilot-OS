-- AI cover letters: generated from the candidate's real resume/profile and the job, editable and saved.
CREATE TABLE IF NOT EXISTS cover_letters (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    job_id UUID NOT NULL,
    application_id UUID,
    resume_id UUID,
    resume_version INT,
    tone VARCHAR(40),
    content TEXT NOT NULL,
    verification_json TEXT,
    edited_by_user BOOLEAN NOT NULL DEFAULT FALSE,
    ai_model VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_cover_letters_user ON cover_letters(user_id, created_at);
