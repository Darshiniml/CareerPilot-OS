-- Companies Table
CREATE TABLE companies (
    id UUID PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    website VARCHAR(255),
    description TEXT,
    industry VARCHAR(100)
);

-- Company Tech Stack Table
CREATE TABLE company_tech_stack (
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    tech_name VARCHAR(100) NOT NULL,
    PRIMARY KEY (company_id, tech_name)
);

-- Jobs Table
CREATE TABLE jobs (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    location VARCHAR(255),
    url VARCHAR(1000),
    salary_range VARCHAR(100),
    employment_type VARCHAR(50), -- Enum values like FULL_TIME, CONTRACT
    experience_level VARCHAR(50), -- Enum values like JUNIOR, SENIOR
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Job Skills Table
CREATE TABLE job_skills (
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    skill_id UUID NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    PRIMARY KEY (job_id, skill_id)
);

-- Applications Table
CREATE TABLE applications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    resume_id UUID REFERENCES resumes(id) ON DELETE SET NULL,
    status VARCHAR(50) NOT NULL, -- ApplicationStatus enum
    applied_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Application History (Audit log for application workflow transitions)
CREATE TABLE application_history (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Interviews Table
CREATE TABLE interviews (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL, -- Phone screen, technical, HR, etc.
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    feedback TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_company_name ON companies(name);
CREATE INDEX idx_jobs_company_id ON jobs(company_id);
CREATE INDEX idx_job_skills_job_id ON job_skills(job_id);
CREATE INDEX idx_applications_user_id ON applications(user_id);
CREATE INDEX idx_applications_job_id ON applications(job_id);
CREATE INDEX idx_app_history_app_id ON application_history(application_id);
CREATE INDEX idx_interviews_app_id ON interviews(application_id);
