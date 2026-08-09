-- Milestone 18: Application Decision & Application Package Tables

CREATE TABLE IF NOT EXISTS application_decisions (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL,
    candidate_id UUID NOT NULL,
    job_id UUID NOT NULL,
    recommendation VARCHAR(50) NOT NULL,
    decision_rationale TEXT,
    strengths_json TEXT,
    critical_gaps_json TEXT,
    recommended_resume_id UUID,
    recommended_resume_title VARCHAR(255),
    company_highlights_json TEXT,
    preflight_result_json TEXT,
    submission_capability_json TEXT,
    evaluated_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS application_packages (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL,
    candidate_id UUID NOT NULL,
    job_id UUID NOT NULL,
    candidate_profile_json TEXT,
    selected_resume_json TEXT,
    job_details_json TEXT,
    match_result_json TEXT,
    company_intelligence_json TEXT,
    decision_id UUID,
    submission_capability_json TEXT,
    preflight_result_json TEXT,
    official_apply_url VARCHAR(1024),
    generated_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_app_decisions_app_id ON application_decisions(application_id);
CREATE INDEX IF NOT EXISTS idx_app_decisions_candidate_job ON application_decisions(candidate_id, job_id);
CREATE INDEX IF NOT EXISTS idx_app_packages_app_id ON application_packages(application_id);
