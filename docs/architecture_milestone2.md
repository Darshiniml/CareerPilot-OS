# CareerPilot OS - Milestone 2 Architecture Diagrams

This document contains ER diagrams, sequence diagrams, and class mappings for Milestone 2 features (User Profiles, Resumes Storage, and Normalized Job Preferences).

---

## 1. Entity Relationship (ER) Diagram

This diagram maps out the PostgreSQL database structures. All major entities utilize UUID primary keys, and junction mappings are fully normalized.

```mermaid
erDiagram
    users {
        UUID id PK
        VARCHAR email UK
        VARCHAR password_hash
        VARCHAR first_name
        VARCHAR last_name
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    roles {
        UUID id PK
        VARCHAR name UK
    }

    user_roles {
        UUID user_id FK
        UUID role_id FK
    }

    user_preferences {
        UUID id PK
        UUID user_id FK, UK
        VARCHAR theme
        BOOLEAN email_notifications
        BOOLEAN auto_apply
        VARCHAR work_style
        INT salary_min
        INT salary_max
        VARCHAR currency_code
        VARCHAR salary_period
        VARCHAR employment_type
        BOOLEAN job_alert_settings
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    social_links {
        UUID id PK
        UUID user_id FK, UK
        VARCHAR linkedin
        VARCHAR github
        VARCHAR portfolio
        VARCHAR twitter
    }

    education {
        UUID id PK
        UUID user_id FK
        VARCHAR institution
        VARCHAR degree
        VARCHAR field_of_study
        DATE start_date
        DATE end_date
        TEXT description
    }

    experience {
        UUID id PK
        UUID user_id FK
        VARCHAR company_name
        VARCHAR title
        VARCHAR location
        DATE start_date
        DATE end_date
        BOOLEAN current_job
        TEXT description
    }

    projects {
        UUID id PK
        UUID user_id FK
        VARCHAR name
        TEXT description
        VARCHAR url
        VARCHAR role
    }

    certifications {
        UUID id PK
        UUID user_id FK
        VARCHAR name
        VARCHAR issuing_organization
        DATE issue_date
        DATE expiration_date
        VARCHAR credential_id
        VARCHAR credential_url
    }

    user_preferred_roles {
        UUID id PK
        UUID user_id FK
        VARCHAR role_name
        TIMESTAMP created_at
    }

    user_preferred_locations {
        UUID id PK
        UUID user_id FK
        VARCHAR location_name
        TIMESTAMP created_at
    }

    user_preferred_companies {
        UUID id PK
        UUID user_id FK
        VARCHAR company_name
        TIMESTAMP created_at
    }

    resumes {
        UUID id PK
        UUID user_id FK
        VARCHAR title
        VARCHAR file_url
        VARCHAR original_filename
        VARCHAR mime_type
        BIGINT file_size
        VARCHAR checksum_sha256
        VARCHAR storage_key
        TIMESTAMP uploaded_at
        UUID uploaded_by FK
        VARCHAR parsing_status
        VARCHAR ai_processing_status
        BOOLEAN is_default
        BOOLEAN is_archived
        BOOLEAN is_deleted
        TIMESTAMP deleted_at
        UUID deleted_by FK
    }

    resume_versions {
        UUID id PK
        UUID resume_id FK
        INT version_number
        VARCHAR file_url
        TEXT parsed_text
        TIMESTAMP created_at
        UUID created_by FK
        VARCHAR change_reason
        BOOLEAN generated_by_ai
    }

    users ||--o{ user_roles : "has"
    roles ||--o{ user_roles : "has"
    users ||--|| user_preferences : "has"
    users ||--|| social_links : "has"
    users ||--o{ education : "has"
    users ||--o{ experience : "has"
    users ||--o{ projects : "has"
    users ||--o{ certifications : "has"
    users ||--o{ user_preferred_roles : "has"
    users ||--o{ user_preferred_locations : "has"
    users ||--o{ user_preferred_companies : "has"
    users ||--o{ resumes : "owns"
    resumes ||--o{ resume_versions : "contains"
```

---

## 2. Sequence Diagram (Resume Upload Flow)

This diagram details the transactional, validation, storage (MinIO), and event flow during resume uploading:

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Nginx
    participant ResumeController
    participant ResumeService
    participant StorageService
    participant ResumeRepository
    participant EventPublisher
    
    Client->>Nginx: POST /api/v1/resumes/upload (Multipart)
    Nginx->>ResumeController: Forward upload request
    Note over ResumeController: Verify JWT & extract Principal email
    ResumeController->>ResumeService: uploadResume(userId, title, filename, contentType, bytes)
    Note over ResumeService: Validate MIME (PDF/Doc) & Size (<10MB)
    Note over ResumeService: Verify active resume count <= 5
    Note over ResumeService: Generate SHA-256 Checksum
    ResumeService->>StorageService: uploadFile(storageKey, contentType, size, stream)
    StorageService-->>ResumeService: return fileUrl
    ResumeService->>ResumeRepository: save(Resume & ResumeVersion)
    ResumeRepository-->>ResumeService: saved entities
    ResumeService->>EventPublisher: publishEvent(ResumeUploadedEvent)
    ResumeService-->>ResumeController: return ResumeDto
    ResumeController-->>Nginx: 201 Created (ResumeDto)
    Nginx-->>Client: return response JSON
```

---

## 3. Class Diagram (Profile Service Separation)

This diagram maps out our clean service separation in the Profile module:

```mermaid
classDiagram
    class ProfileController {
        -ProfileService profileService
        -PersonalInfoService personalInfoService
        -EducationService educationService
        -ExperienceService experienceService
        -ProjectService projectService
        -CertificationService certificationService
        -SocialLinkService socialLinkService
        -PreferenceService preferenceService
        +getProfile(principal)
        +updatePersonalInfo(principal, dto)
        +updatePreferences(principal, dto)
        +updateSocialLinks(principal, dto)
        +getEducation(principal, page, size)
        +addEducation(principal, dto)
        +getExperience(principal, page, size)
        +addExperience(principal, dto)
        +getProjects(principal, page, size)
        +addProjects(principal, dto)
        +getCertifications(principal, page, size)
        +addCertification(principal, dto)
    }

    class ProfileService {
        -UserRepository userRepository
        -SocialLinkService socialLinkService
        -EducationService educationService
        -ExperienceService experienceService
        -ProjectService projectService
        -CertificationService certificationService
        -PreferenceService preferenceService
        +getProfile(userId) ProfileDto
    }

    class PersonalInfoService {
        -UserRepository userRepository
        -ApplicationEventPublisher eventPublisher
        +updatePersonalInfo(userId, firstName, lastName, email) User
    }

    class EducationService {
        -EducationRepository educationRepository
        -UserRepository userRepository
        -ApplicationEventPublisher eventPublisher
        +getEducation(userId, pageable) Page
        +addEducation(userId, dto) Education
        +deleteEducation(userId, id)
    }

    class ExperienceService {
        -ExperienceRepository experienceRepository
        -UserRepository userRepository
        +getExperience(userId, pageable) Page
        +addExperience(userId, dto) Experience
        +deleteExperience(userId, id)
    }

    class ProjectService {
        -ProjectRepository projectRepository
        -UserRepository userRepository
        -ApplicationEventPublisher eventPublisher
        +getProjects(userId, pageable) Page
        +addProject(userId, dto) Project
        +deleteProject(userId, id)
    }

    class CertificationService {
        -CertificationRepository certificationRepository
        -UserRepository userRepository
        +getCertifications(userId, pageable) Page
        +addCertification(userId, dto) Certification
        +deleteCertification(userId, id)
    }

    class SocialLinkService {
        -SocialLinksRepository socialLinksRepository
        -UserRepository userRepository
        +getSocialLinks(userId) SocialLinks
        +updateSocialLinks(userId, dto) SocialLinks
    }

    class PreferenceService {
        -UserPreferenceRepository userPreferenceRepository
        -UserRepository userRepository
        -UserPreferredRoleRepository preferredRoleRepository
        -UserPreferredLocationRepository preferredLocationRepository
        -UserPreferredCompanyRepository preferredCompanyRepository
        -ApplicationEventPublisher eventPublisher
        +getPreferences(userId) PreferencesDto
        +updatePreferences(userId, dto) UserPreference
    }

    ProfileController --> ProfileService
    ProfileController --> PersonalInfoService
    ProfileController --> EducationService
    ProfileController --> ExperienceService
    ProfileController --> ProjectService
    ProfileController --> CertificationService
    ProfileController --> SocialLinkService
    ProfileController --> PreferenceService

    ProfileService --> SocialLinkService
    ProfileService --> EducationService
    ProfileService --> ExperienceService
    ProfileService --> ProjectService
    ProfileService --> CertificationService
    ProfileService --> PreferenceService
```
