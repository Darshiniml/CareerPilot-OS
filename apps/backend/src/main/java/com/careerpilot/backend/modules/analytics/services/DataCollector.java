package com.careerpilot.backend.modules.analytics.services;

import com.careerpilot.backend.modules.ai.resume.domain.ResumeIntelligenceCache;
import com.careerpilot.backend.modules.ai.resume.repositories.ResumeIntelligenceCacheRepository;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserPreference;
import com.careerpilot.backend.modules.auth.domain.UserPreferenceRepository;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.backend.modules.discovery.repositories.DiscoveryJobRepository;
import com.careerpilot.backend.modules.interview.domain.InterviewSession;
import com.careerpilot.backend.modules.interview.repositories.InterviewSessionRepository;
import com.careerpilot.backend.modules.profile.domain.Certification;
import com.careerpilot.backend.modules.profile.domain.Education;
import com.careerpilot.backend.modules.profile.domain.Experience;
import com.careerpilot.backend.modules.profile.domain.Project;
import com.careerpilot.backend.modules.profile.repositories.CertificationRepository;
import com.careerpilot.backend.modules.profile.repositories.EducationRepository;
import com.careerpilot.backend.modules.profile.repositories.ExperienceRepository;
import com.careerpilot.backend.modules.profile.repositories.ProjectRepository;
import com.careerpilot.backend.modules.resume.domain.Resume;
import com.careerpilot.backend.modules.resume.repositories.ResumeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class DataCollector {

    private final ResumeRepository resumeRepository;
    private final ResumeIntelligenceCacheRepository resumeCacheRepository;
    private final DiscoveryJobRepository discoveryJobRepository;
    private final ApplicationRecordRepository applicationRecordRepository;
    private final InterviewSessionRepository interviewSessionRepository;
    private final UserRepository userRepository;
    private final EducationRepository educationRepository;
    private final ExperienceRepository experienceRepository;
    private final ProjectRepository projectRepository;
    private final CertificationRepository certificationRepository;
    private final UserPreferenceRepository userPreferenceRepository;

    public Map<String, Object> collectCandidateData(UUID candidateId) {
        Map<String, Object> data = new HashMap<>();

        // 1. User Info
        User user = userRepository.findById(candidateId).orElse(null);
        data.put("user", user);

        // 2. Default Resume & ATS Cache
        Optional<Resume> defaultResume = resumeRepository.findDefaultByUserId(candidateId);
        if (defaultResume.isEmpty()) {
            List<Resume> activeResumes = resumeRepository.findActiveByUserId(candidateId);
            if (!activeResumes.isEmpty()) {
                defaultResume = Optional.of(activeResumes.get(0));
            }
        }
        data.put("resume", defaultResume.orElse(null));

        ResumeIntelligenceCache resumeCache = null;
        if (defaultResume.isPresent()) {
            String checksum = defaultResume.get().getChecksumSha256();
            if (checksum != null) {
                resumeCache = resumeCacheRepository.findById(checksum).orElse(null);
            }
            if (resumeCache == null) {
                List<ResumeIntelligenceCache> caches = resumeCacheRepository.findAll();
                if (!caches.isEmpty()) {
                    resumeCache = caches.get(0);
                }
            }
        }
        data.put("resumeCache", resumeCache);

        // 3. Jobs Discovered & Applications
        long totalJobsDiscovered = discoveryJobRepository.count();
        List<ApplicationRecord> applications = applicationRecordRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId);
        data.put("totalJobsDiscovered", totalJobsDiscovered);
        data.put("applications", applications);

        // 4. Interviews
        List<InterviewSession> interviews = interviewSessionRepository.findByCandidateId(candidateId);
        data.put("interviews", interviews);

        // 5. Profile Sections
        List<Education> education = educationRepository.findByUserId(candidateId);
        List<Experience> experience = experienceRepository.findByUserId(candidateId);
        List<Project> projects = projectRepository.findByUserId(candidateId);
        List<Certification> certifications = certificationRepository.findByUserId(candidateId);
        UserPreference preferences = userPreferenceRepository.findById(candidateId).orElse(null);

        data.put("education", education);
        data.put("experience", experience);
        data.put("projects", projects);
        data.put("certifications", certifications);
        data.put("preferences", preferences);

        return data;
    }
}
