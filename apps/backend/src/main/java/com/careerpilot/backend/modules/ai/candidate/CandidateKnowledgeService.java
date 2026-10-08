package com.careerpilot.backend.modules.ai.candidate;

import com.careerpilot.backend.modules.ai.knowledge.domain.AiDocument;
import com.careerpilot.backend.modules.ai.knowledge.repositories.AiDocumentRepository;
import com.careerpilot.backend.modules.profile.services.ProfileService;
import com.careerpilot.backend.modules.resume.domain.Resume;
import com.careerpilot.backend.modules.resume.domain.ResumeVersion;
import com.careerpilot.backend.modules.resume.repositories.ResumeRepository;
import com.careerpilot.backend.modules.resume.repositories.ResumeVersionRepository;
import com.careerpilot.shared.dto.profile.PreferencesDto;
import com.careerpilot.shared.dto.profile.ProfileDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Single source of a candidate's real data for AI features (matching, interview coach, cover letters,
 * copilot...). Always keyed by the authenticated user's id; never accepts client-supplied knowledge.
 */
@Service
@RequiredArgsConstructor
public class CandidateKnowledgeService {

    private final ResumeRepository resumeRepository;
    private final ResumeVersionRepository versionRepository;
    private final AiDocumentRepository documentRepository;
    private final ProfileService profileService;

    /** A processed resume version: parsed knowledge, ATS metrics and raw text. */
    public record ResumeKnowledge(UUID resumeId, UUID documentId, int versionNumber, String title,
                                  Map<String, Object> knowledge, Map<String, Object> atsMetrics, String text) {
    }

    /** The default resume if processed, otherwise the most recently processed active resume. */
    @Transactional(readOnly = true)
    public Optional<ResumeKnowledge> primaryResume(UUID userId) {
        Optional<Resume> defaultResume = resumeRepository.findDefaultByUserId(userId);
        if (defaultResume.isPresent()) {
            Optional<ResumeKnowledge> knowledge = resumeKnowledge(userId, defaultResume.get().getId());
            if (knowledge.isPresent()) {
                return knowledge;
            }
        }
        return resumeRepository.findActiveByUserId(userId).stream()
                .sorted(Comparator.comparing(Resume::getUploadedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(r -> resumeKnowledge(userId, r.getId()))
                .flatMap(Optional::stream)
                .findFirst();
    }

    /** Newest processed version of one resume, after verifying ownership. */
    @Transactional(readOnly = true)
    public Optional<ResumeKnowledge> resumeKnowledge(UUID userId, UUID resumeId) {
        Resume resume = resumeRepository.findActiveById(resumeId).orElse(null);
        if (resume == null || !resume.getUser().getId().equals(userId)) {
            return Optional.empty();
        }
        for (ResumeVersion version : versionRepository.findByResumeIdOrderByVersionNumberDesc(resumeId)) {
            Optional<AiDocument> doc = documentRepository.findById(version.getId())
                    .filter(d -> userId.equals(d.getOwnerId()))
                    .filter(d -> "READY".equals(d.getStatus()))
                    .filter(d -> d.getStructuredMetadata() != null && !d.getStructuredMetadata().isEmpty());
            if (doc.isPresent()) {
                AiDocument d = doc.get();
                return Optional.of(new ResumeKnowledge(resumeId, d.getId(), version.getVersionNumber(), resume.getTitle(),
                        d.getStructuredMetadata(), d.getFlexibleMetadata() != null ? d.getFlexibleMetadata() : Map.of(),
                        d.getContent()));
            }
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> preferences(UUID userId) {
        ProfileDto profile = profileService.getProfile(userId);
        PreferencesDto p = profile != null ? profile.getPreferences() : null;
        Map<String, Object> prefs = new LinkedHashMap<>();
        if (p == null) {
            return prefs;
        }
        putIfPresent(prefs, "workStyle", p.getWorkStyle());
        putIfPresent(prefs, "salaryMin", p.getSalaryMin());
        putIfPresent(prefs, "salaryMax", p.getSalaryMax());
        putIfPresent(prefs, "salaryCurrency", p.getCurrencyCode());
        putIfPresent(prefs, "employmentType", p.getEmploymentType());
        putIfPresent(prefs, "preferredRoles", p.getPreferredRoles());
        putIfPresent(prefs, "preferredLocations", p.getPreferredLocations());
        putIfPresent(prefs, "preferredCompanies", p.getPreferredCompanies());
        return prefs;
    }

    /**
     * Compact candidate summary for model context. Contact details (email/phone) are deliberately
     * excluded: models never need them to reason about a career.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> candidateSummary(UUID userId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        ProfileDto profile = profileService.getProfile(userId);
        if (profile != null) {
            putIfPresent(summary, "firstName", profile.getFirstName());
            putIfPresent(summary, "profileSkills", profile.getSkills());
            putIfPresent(summary, "profileExperience", profile.getExperience());
            putIfPresent(summary, "profileEducation", profile.getEducation());
            putIfPresent(summary, "profileProjects", profile.getProjects());
            putIfPresent(summary, "profileCertifications", profile.getCertifications());
        }
        summary.put("preferences", preferences(userId));
        primaryResume(userId).ifPresent(r -> {
            Map<String, Object> k = r.knowledge();
            Map<String, Object> resume = new LinkedHashMap<>();
            resume.put("resumeTitle", r.title());
            putIfPresent(resume, "summary", k.get("summary"));
            putIfPresent(resume, "skills", k.get("skills"));
            putIfPresent(resume, "experience", k.get("experience"));
            putIfPresent(resume, "education", k.get("education"));
            putIfPresent(resume, "projects", k.get("projects"));
            putIfPresent(resume, "certifications", k.get("certifications"));
            putIfPresent(resume, "experienceIntelligence", k.get("intelligence"));
            summary.put("resume", resume);
        });
        return summary;
    }

    /** Skill names from the resume and the profile, de-duplicated, as written. */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<String> skillNames(UUID userId) {
        LinkedHashMap<String, String> skills = new LinkedHashMap<>();
        primaryResume(userId).ifPresent(r -> {
            Object raw = r.knowledge().get("skills");
            if (raw instanceof List<?> list) {
                for (Object o : list) {
                    Object name = o instanceof Map<?, ?> m ? ((Map<String, Object>) m).get("skill") : o;
                    if (name != null && !String.valueOf(name).isBlank()) {
                        skills.putIfAbsent(String.valueOf(name).toLowerCase(Locale.ROOT), String.valueOf(name));
                    }
                }
            }
        });
        ProfileDto profile = profileService.getProfile(userId);
        if (profile != null && profile.getSkills() != null) {
            profile.getSkills().forEach(s -> skills.putIfAbsent(s.toLowerCase(Locale.ROOT), s));
        }
        return new ArrayList<>(skills.values());
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Collection<?> c && c.isEmpty()) {
            return;
        }
        if (value instanceof String s && s.isBlank()) {
            return;
        }
        map.put(key, value);
    }
}
