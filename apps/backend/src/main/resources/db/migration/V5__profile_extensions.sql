-- Certifications Table
CREATE TABLE certifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    issuing_organization VARCHAR(255) NOT NULL,
    issue_date DATE,
    expiration_date DATE,
    credential_id VARCHAR(100),
    credential_url VARCHAR(1000),
    CONSTRAINT unique_user_certification UNIQUE(user_id, name)
);

-- Social Links Table
CREATE TABLE social_links (
    id UUID PRIMARY KEY,
    user_id UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    linkedin VARCHAR(255),
    github VARCHAR(255),
    portfolio VARCHAR(255),
    twitter VARCHAR(255)
);

-- Alter user_preferences
ALTER TABLE user_preferences 
ADD COLUMN IF NOT EXISTS work_style VARCHAR(50) DEFAULT 'REMOTE',
ADD COLUMN IF NOT EXISTS salary_min INT,
ADD COLUMN IF NOT EXISTS salary_max INT,
ADD COLUMN IF NOT EXISTS currency_code VARCHAR(3) DEFAULT 'USD',
ADD COLUMN IF NOT EXISTS salary_period VARCHAR(20) DEFAULT 'YEARLY',
ADD COLUMN IF NOT EXISTS employment_type VARCHAR(50) DEFAULT 'FULL_TIME',
ADD COLUMN IF NOT EXISTS job_alert_settings BOOLEAN DEFAULT TRUE;

-- Normalized Preferences: Preferred Roles
CREATE TABLE user_preferred_roles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_user_role UNIQUE(user_id, role_name)
);

-- Normalized Preferences: Preferred Locations
CREATE TABLE user_preferred_locations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    location_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_user_location UNIQUE(user_id, location_name)
);

-- Normalized Preferences: Preferred Companies
CREATE TABLE user_preferred_companies (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    company_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_user_company UNIQUE(user_id, company_name)
);

-- Alter resumes to support metadata, checksums, soft deletes, and defaults
ALTER TABLE resumes 
ADD COLUMN IF NOT EXISTS original_filename VARCHAR(255),
ADD COLUMN IF NOT EXISTS mime_type VARCHAR(100),
ADD COLUMN IF NOT EXISTS file_size BIGINT,
ADD COLUMN checksum_sha256 VARCHAR(64),
ADD COLUMN IF NOT EXISTS storage_key VARCHAR(1000),
ADD COLUMN IF NOT EXISTS uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
ADD COLUMN IF NOT EXISTS uploaded_by UUID REFERENCES users(id) ON DELETE SET NULL,
ADD COLUMN IF NOT EXISTS parsing_status VARCHAR(50) DEFAULT 'PENDING',
ADD COLUMN IF NOT EXISTS ai_processing_status VARCHAR(50) DEFAULT 'PENDING',
ADD COLUMN IF NOT EXISTS is_default BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS is_archived BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN DEFAULT FALSE,
ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP WITH TIME ZONE,
ADD COLUMN IF NOT EXISTS deleted_by UUID REFERENCES users(id) ON DELETE SET NULL;

-- Alter resume_versions to track change logs and AI flags
ALTER TABLE resume_versions
ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id) ON DELETE SET NULL,
ADD COLUMN IF NOT EXISTS change_reason VARCHAR(500),
ADD COLUMN IF NOT EXISTS generated_by_ai BOOLEAN DEFAULT FALSE;

-- Indexes
CREATE INDEX idx_certifications_user_id ON certifications(user_id);
CREATE INDEX idx_pref_roles_user ON user_preferred_roles(user_id);
CREATE INDEX idx_pref_locs_user ON user_preferred_locations(user_id);
CREATE INDEX idx_pref_comps_user ON user_preferred_companies(user_id);
