-- V12__analytics_and_learning_engine.sql

-- Interview module tables (needed for JPA validation and persistence)
CREATE TABLE interview_knowledge (
    interview_id UUID PRIMARY KEY,
    application_id UUID NOT NULL,
    candidate_id UUID NOT NULL,
    company_id UUID,
    job_id UUID,
    interview_stage VARCHAR(50),
    interview_type VARCHAR(50),
    estimated_difficulty VARCHAR(50),
    preparation_priority VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE interview_required_technologies (
    interview_id UUID NOT NULL REFERENCES interview_knowledge(interview_id) ON DELETE CASCADE,
    technology VARCHAR(255) NOT NULL
);

CREATE TABLE interview_required_concepts (
    interview_id UUID NOT NULL REFERENCES interview_knowledge(interview_id) ON DELETE CASCADE,
    concept VARCHAR(255) NOT NULL
);

CREATE TABLE interview_behavioral_topics (
    interview_id UUID NOT NULL REFERENCES interview_knowledge(interview_id) ON DELETE CASCADE,
    topic VARCHAR(255) NOT NULL
);

CREATE TABLE interview_coding_topics (
    interview_id UUID NOT NULL REFERENCES interview_knowledge(interview_id) ON DELETE CASCADE,
    topic VARCHAR(255) NOT NULL
);

CREATE TABLE interview_system_design_topics (
    interview_id UUID NOT NULL REFERENCES interview_knowledge(interview_id) ON DELETE CASCADE,
    topic VARCHAR(255) NOT NULL
);

CREATE TABLE interview_preparation_checklist (
    interview_id UUID NOT NULL REFERENCES interview_knowledge(interview_id) ON DELETE CASCADE,
    item VARCHAR(500) NOT NULL
);

CREATE TABLE interview_sessions (
    session_id UUID PRIMARY KEY,
    application_id UUID NOT NULL,
    candidate_id UUID NOT NULL,
    interview_type VARCHAR(50),
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    overall_readiness DOUBLE PRECISION NOT NULL DEFAULT 0.0
);

CREATE TABLE interview_questions (
    question_id UUID PRIMARY KEY,
    session_id UUID REFERENCES interview_sessions(session_id) ON DELETE CASCADE,
    question_text TEXT NOT NULL,
    category VARCHAR(255),
    candidate_answer TEXT,
    time_taken_seconds INT,
    correctness_score DOUBLE PRECISION,
    completeness_score DOUBLE PRECISION,
    technical_accuracy_score DOUBLE PRECISION,
    communication_score DOUBLE PRECISION,
    examples_score DOUBLE PRECISION,
    confidence_score DOUBLE PRECISION,
    structure_score DOUBLE PRECISION,
    evaluation_feedback TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- Analytics & Learning Engine tables
CREATE TABLE career_analytics (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    analysis_period VARCHAR(50) NOT NULL, -- DAILY, WEEKLY, MONTHLY, QUARTERLY
    total_jobs_discovered INT NOT NULL DEFAULT 0,
    jobs_matched INT NOT NULL DEFAULT 0,
    applications_submitted INT NOT NULL DEFAULT 0,
    applications_successful INT NOT NULL DEFAULT 0,
    interviews_received INT NOT NULL DEFAULT 0,
    offers_received INT NOT NULL DEFAULT 0,
    average_match_score DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    average_interview_readiness DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    resume_score DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    skill_coverage DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    application_success_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    interview_conversion_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    offer_conversion_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    top_skills TEXT, -- Comma-separated list
    missing_skills TEXT, -- Comma-separated list
    trending_skills TEXT, -- Comma-separated list
    career_growth_score DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE skill_demand_snapshots (
    id UUID PRIMARY KEY,
    skill VARCHAR(100) NOT NULL,
    demand_percentage DOUBLE PRECISION NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE skill_gaps (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    skill VARCHAR(100) NOT NULL,
    demand_percentage DOUBLE PRECISION NOT NULL,
    current_level VARCHAR(50) NOT NULL, -- BEGINNER, INTERMEDIATE, ADVANCED, MISSING
    match_improvement_potential DOUBLE PRECISION NOT NULL,
    priority VARCHAR(50) NOT NULL, -- HIGH, MEDIUM, LOW
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE learning_paths (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    skill VARCHAR(100) NOT NULL,
    current_level VARCHAR(50) NOT NULL,
    target_level VARCHAR(50) NOT NULL,
    priority VARCHAR(50) NOT NULL,
    estimated_hours INT NOT NULL,
    expected_match_improvement DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE learning_path_items (
    id UUID PRIMARY KEY,
    learning_path_id UUID NOT NULL REFERENCES learning_paths(id) ON DELETE CASCADE,
    step_name VARCHAR(255) NOT NULL,
    sequence_number INT NOT NULL,
    prerequisite_steps TEXT -- Comma-separated dependencies
);

CREATE TABLE learning_progress (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    skill VARCHAR(100) NOT NULL,
    progress_percentage DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    status VARCHAR(50) NOT NULL, -- NOT_STARTED, IN_PROGRESS, COMPLETED
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE career_goals (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_role VARCHAR(255) NOT NULL,
    target_industry VARCHAR(255),
    target_salary DOUBLE PRECISION,
    target_location VARCHAR(255),
    timeline_months INT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE career_goal_progress (
    id UUID PRIMARY KEY,
    goal_id UUID NOT NULL REFERENCES career_goals(id) ON DELETE CASCADE,
    current_state TEXT,
    required_skills TEXT,
    required_experience TEXT,
    required_projects TEXT,
    learning_plan_progress DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    overall_progress DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE career_metrics (
    id UUID PRIMARY KEY,
    candidate_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    metric_type VARCHAR(100) NOT NULL,
    metric_value DOUBLE PRECISION NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_career_analytics_candidate ON career_analytics(candidate_id);
CREATE INDEX idx_skill_demand_captured ON skill_demand_snapshots(captured_at);
CREATE INDEX idx_skill_gaps_candidate ON skill_gaps(candidate_id);
CREATE INDEX idx_learning_paths_candidate ON learning_paths(candidate_id);
CREATE INDEX idx_learning_progress_candidate ON learning_progress(candidate_id);
CREATE INDEX idx_career_goals_candidate ON career_goals(candidate_id);
CREATE INDEX idx_career_metrics_candidate ON career_metrics(candidate_id);
