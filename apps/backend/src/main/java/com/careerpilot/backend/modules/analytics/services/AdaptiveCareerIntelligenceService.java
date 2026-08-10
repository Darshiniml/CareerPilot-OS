package com.careerpilot.backend.modules.analytics.services;

import com.careerpilot.backend.modules.application.domain.ApplicationHistory;
import com.careerpilot.backend.modules.application.domain.ApplicationRecord;
import com.careerpilot.backend.modules.application.domain.WorkflowState;
import com.careerpilot.backend.modules.application.repositories.ApplicationHistoryRepository;
import com.careerpilot.backend.modules.application.repositories.ApplicationRecordRepository;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.repositories.DiscoveryJobRepository;
import com.careerpilot.backend.modules.resume.domain.Resume;
import com.careerpilot.backend.modules.resume.repositories.ResumeRepository;
import com.careerpilot.backend.modules.ai.resume.domain.ResumeIntelligenceCache;
import com.careerpilot.backend.modules.ai.resume.repositories.ResumeIntelligenceCacheRepository;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdaptiveCareerIntelligenceService {

    private final ApplicationRecordRepository applicationRepository;
    private final ApplicationHistoryRepository historyRepository;
    private final DiscoveryJobRepository jobRepository;
    private final ResumeRepository resumeRepository;
    private final ResumeIntelligenceCacheRepository resumeIntelligenceCacheRepository;

    private static final int MIN_DIMENSION_SAMPLE = 5;

    public CareerOutcomeProfile getCareerOutcomeProfile(UUID candidateId) {
        List<ApplicationRecord> apps = applicationRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId);
        int totalApps = apps.size();

        long totalApproved = apps.stream()
                .filter(a -> a.getWorkflowState().ordinal() >= WorkflowState.APPROVED.ordinal() 
                        && a.getWorkflowState() != WorkflowState.FAILED 
                        && a.getWorkflowState() != WorkflowState.APPLICATION_FAILED
                        && a.getWorkflowState() != WorkflowState.SUBMISSION_FAILED)
                .count();

        Set<WorkflowState> submittedStates = Set.of(
                WorkflowState.SUBMITTED, WorkflowState.SUBMITTED_VERIFIED, WorkflowState.VERIFICATION_PENDING,
                WorkflowState.UNDER_REVIEW, WorkflowState.ASSESSMENT, WorkflowState.INTERVIEW,
                WorkflowState.OFFER, WorkflowState.REJECTED, WorkflowState.REJECTED_BY_COMPANY,
                WorkflowState.WITHDRAWN, WorkflowState.COMPLETED
        );

        long totalSubmitted = apps.stream()
                .filter(a -> submittedStates.contains(a.getWorkflowState()))
                .count();

        Set<WorkflowState> verifiedStates = Set.of(
                WorkflowState.SUBMITTED_VERIFIED, WorkflowState.UNDER_REVIEW, WorkflowState.ASSESSMENT,
                WorkflowState.INTERVIEW, WorkflowState.OFFER, WorkflowState.REJECTED,
                WorkflowState.REJECTED_BY_COMPANY, WorkflowState.WITHDRAWN, WorkflowState.COMPLETED
        );

        long totalVerified = apps.stream()
                .filter(a -> verifiedStates.contains(a.getWorkflowState()))
                .count();

        long totalInterviews = 0;
        long totalOffers = 0;
        long totalRejections = 0;

        List<Double> interviewTimes = new ArrayList<>();
        List<Double> offerTimes = new ArrayList<>();

        for (ApplicationRecord app : apps) {
            List<ApplicationHistory> history = historyRepository.findByApplicationIdOrderByCreatedAtAsc(app.getApplicationId());
            boolean hasInterview = history.stream().anyMatch(h -> h.getToState() == WorkflowState.INTERVIEW) 
                    || app.getWorkflowState() == WorkflowState.INTERVIEW;
            boolean hasOffer = history.stream().anyMatch(h -> h.getToState() == WorkflowState.OFFER)
                    || app.getWorkflowState() == WorkflowState.OFFER;
            boolean hasRejection = history.stream().anyMatch(h -> h.getToState() == WorkflowState.REJECTED || h.getToState() == WorkflowState.REJECTED_BY_COMPANY)
                    || app.getWorkflowState() == WorkflowState.REJECTED || app.getWorkflowState() == WorkflowState.REJECTED_BY_COMPANY;

            if (hasInterview) totalInterviews++;
            if (hasOffer) totalOffers++;
            if (hasRejection) totalRejections++;

            Instant start = app.getSubmittedAt() != null ? app.getSubmittedAt() : app.getCreatedAt();
            if (hasInterview && start != null) {
                history.stream()
                        .filter(h -> h.getToState() == WorkflowState.INTERVIEW)
                        .findFirst()
                        .ifPresent(h -> {
                            double days = Duration.between(start, h.getCreatedAt()).toHours() / 24.0;
                            interviewTimes.add(Math.max(0.0, days));
                        });
            }

            if (hasOffer && start != null) {
                history.stream()
                        .filter(h -> h.getToState() == WorkflowState.OFFER)
                        .findFirst()
                        .ifPresent(h -> {
                            double days = Duration.between(start, h.getCreatedAt()).toHours() / 24.0;
                            offerTimes.add(Math.max(0.0, days));
                        });
            }
        }

        double interviewRate = totalApps > 0 ? (double) totalInterviews / totalApps : 0.0;
        double offerRate = totalApps > 0 ? (double) totalOffers / totalApps : 0.0;
        double responseRate = totalApps > 0 ? (double) (totalInterviews + totalOffers + totalRejections) / totalApps : 0.0;

        double avgTimeToInterview = interviewTimes.isEmpty() ? 0.0 : interviewTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgTimeToOffer = offerTimes.isEmpty() ? 0.0 : offerTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        return CareerOutcomeProfile.builder()
                .candidateId(candidateId)
                .totalApplications(totalApps)
                .totalApproved((int) totalApproved)
                .totalSubmitted((int) totalSubmitted)
                .totalVerified((int) totalVerified)
                .totalInterviews((int) totalInterviews)
                .totalOffers((int) totalOffers)
                .totalRejections((int) totalRejections)
                .interviewRate(interviewRate)
                .offerRate(offerRate)
                .responseRate(responseRate)
                .averageTimeToInterview(avgTimeToInterview)
                .averageTimeToOffer(avgTimeToOffer)
                .build();
    }

    public List<RolePerformance> getRolePerformance(UUID candidateId) {
        List<ApplicationRecord> apps = applicationRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId);
        Map<String, List<ApplicationRecord>> groupedByRole = new HashMap<>();

        for (ApplicationRecord app : apps) {
            String roleName = "Unknown Role";
            if (app.getJobId() != null) {
                try {
                    Optional<DiscoveryJob> jobOpt = jobRepository.findById(app.getJobId());
                    if (jobOpt.isPresent() && jobOpt.get().getNormalizedTitle() != null) {
                        roleName = jobOpt.get().getNormalizedTitle();
                    } else if (jobOpt.isPresent() && jobOpt.get().getTitle() != null) {
                        roleName = jobOpt.get().getTitle();
                    }
                } catch (Exception e) {
                    log.warn("Error fetching job for role performance: ", e);
                }
            }
            groupedByRole.computeIfAbsent(roleName, k -> new ArrayList<>()).add(app);
        }

        List<RolePerformance> list = new ArrayList<>();
        for (Map.Entry<String, List<ApplicationRecord>> entry : groupedByRole.entrySet()) {
            String role = entry.getKey();
            List<ApplicationRecord> roleApps = entry.getValue();
            int count = roleApps.size();

            long interviews = countInterviews(roleApps);
            long offers = countOffers(roleApps);
            long rejections = countRejections(roleApps);
            long submitted = countSubmitted(roleApps);
            long verified = countVerified(roleApps);

            String confidence = count >= MIN_DIMENSION_SAMPLE ? "SUFFICIENT" : "INSUFFICIENT_DATA";
            double interviewRate = count >= MIN_DIMENSION_SAMPLE ? (double) interviews / count : 0.0;
            double offerRate = count >= MIN_DIMENSION_SAMPLE ? (double) offers / count : 0.0;

            list.add(RolePerformance.builder()
                    .roleName(role)
                    .applications(count)
                    .submitted((int) submitted)
                    .verified((int) verified)
                    .interviews((int) interviews)
                    .offers((int) offers)
                    .rejections((int) rejections)
                    .interviewRate(interviewRate)
                    .offerRate(offerRate)
                    .confidenceStatus(confidence)
                    .build());
        }

        return list;
    }

    public List<SkillPerformance> getSkillPerformance(UUID candidateId) {
        List<ApplicationRecord> apps = applicationRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId);
        Map<String, List<ApplicationRecord>> skillApps = new HashMap<>();

        Map<UUID, Set<String>> resumeSkillsMap = new HashMap<>();

        for (ApplicationRecord app : apps) {
            UUID resumeId = app.getSelectedResumeId();
            if (resumeId == null && app.getMetadata() != null) {
                String rIdStr = String.valueOf(app.getMetadata().get("resumeId"));
                if (rIdStr != null && !"null".equals(rIdStr)) {
                    try {
                        resumeId = UUID.fromString(rIdStr);
                    } catch (Exception ignored) {}
                }
            }

            Set<String> skills = Set.of();
            if (resumeId != null) {
                skills = resumeSkillsMap.computeIfAbsent(resumeId, id -> {
                    try {
                        Optional<Resume> resumeOpt = resumeRepository.findActiveById(id);
                        if (resumeOpt.isPresent() && resumeOpt.get().getChecksumSha256() != null) {
                            Optional<ResumeIntelligenceCache> cacheOpt = resumeIntelligenceCacheRepository.findById(resumeOpt.get().getChecksumSha256());
                            if (cacheOpt.isPresent() && cacheOpt.get().getStructuredKnowledge() != null) {
                                Map<String, Object> knowledge = cacheOpt.get().getStructuredKnowledge();
                                if (knowledge.containsKey("skills")) {
                                    List<?> rawSkills = (List<?>) knowledge.get("skills");
                                    Set<String> parsedSkills = new HashSet<>();
                                    for (Object item : rawSkills) {
                                        if (item instanceof Map<?, ?> map) {
                                            Object skillVal = map.get("skill");
                                            if (skillVal == null) skillVal = map.get("name");
                                            if (skillVal != null) parsedSkills.add(skillVal.toString().toLowerCase().trim());
                                        } else if (item != null) {
                                            parsedSkills.add(item.toString().toLowerCase().trim());
                                        }
                                    }
                                    return parsedSkills;
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Error fetching resume skills: ", e);
                    }
                    return Set.of();
                });
            }

            for (String skill : skills) {
                skillApps.computeIfAbsent(skill, k -> new ArrayList<>()).add(app);
            }
        }

        List<SkillPerformance> list = new ArrayList<>();
        for (Map.Entry<String, List<ApplicationRecord>> entry : skillApps.entrySet()) {
            String skill = entry.getKey();
            List<ApplicationRecord> appsWithSkill = entry.getValue();
            int count = appsWithSkill.size();

            long interviews = countInterviews(appsWithSkill);
            long offers = countOffers(appsWithSkill);

            String confidence = count >= MIN_DIMENSION_SAMPLE ? "SUFFICIENT" : "INSUFFICIENT_DATA";
            double interviewRate = count >= MIN_DIMENSION_SAMPLE ? (double) interviews / count : 0.0;
            double offerRate = count >= MIN_DIMENSION_SAMPLE ? (double) offers / count : 0.0;

            list.add(SkillPerformance.builder()
                    .skillName(skill)
                    .applications(count)
                    .interviews((int) interviews)
                    .offers((int) offers)
                    .interviewRate(interviewRate)
                    .offerRate(offerRate)
                    .confidenceStatus(confidence)
                    .build());
        }

        return list;
    }

    public List<CompanyPerformance> getCompanyPerformance(UUID candidateId) {
        List<ApplicationRecord> apps = applicationRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId);
        Map<String, List<ApplicationRecord>> groupedByCompany = new HashMap<>();

        for (ApplicationRecord app : apps) {
            String companyName = "Unknown Company";
            if (app.getJobId() != null) {
                try {
                    Optional<DiscoveryJob> jobOpt = jobRepository.findById(app.getJobId());
                    if (jobOpt.isPresent() && jobOpt.get().getNormalizedCompany() != null) {
                        companyName = jobOpt.get().getNormalizedCompany();
                    } else if (jobOpt.isPresent() && jobOpt.get().getCompany() != null) {
                        companyName = jobOpt.get().getCompany();
                    }
                } catch (Exception e) {
                    log.warn("Error fetching job for company performance: ", e);
                }
            }
            groupedByCompany.computeIfAbsent(companyName, k -> new ArrayList<>()).add(app);
        }

        List<CompanyPerformance> list = new ArrayList<>();
        for (Map.Entry<String, List<ApplicationRecord>> entry : groupedByCompany.entrySet()) {
            String company = entry.getKey();
            List<ApplicationRecord> compApps = entry.getValue();
            int count = compApps.size();

            long interviews = countInterviews(compApps);
            long offers = countOffers(compApps);
            long rejections = countRejections(compApps);

            String confidence = count >= MIN_DIMENSION_SAMPLE ? "SUFFICIENT" : "INSUFFICIENT_DATA";
            double interviewRate = count >= MIN_DIMENSION_SAMPLE ? (double) interviews / count : 0.0;
            double offerRate = count >= MIN_DIMENSION_SAMPLE ? (double) offers / count : 0.0;

            list.add(CompanyPerformance.builder()
                    .companyName(company)
                    .applications(count)
                    .interviews((int) interviews)
                    .offers((int) offers)
                    .rejections((int) rejections)
                    .interviewRate(interviewRate)
                    .offerRate(offerRate)
                    .confidenceStatus(confidence)
                    .build());
        }

        return list;
    }

    public List<LocationPerformance> getLocationPerformance(UUID candidateId) {
        List<ApplicationRecord> apps = applicationRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId);
        Map<String, List<ApplicationRecord>> groupedByLoc = new HashMap<>();

        for (ApplicationRecord app : apps) {
            String loc = "Unknown Location";
            if (app.getJobId() != null) {
                try {
                    Optional<DiscoveryJob> jobOpt = jobRepository.findById(app.getJobId());
                    if (jobOpt.isPresent() && jobOpt.get().getLocation() != null) {
                        loc = jobOpt.get().getLocation();
                    }
                } catch (Exception e) {
                    log.warn("Error fetching job for location performance: ", e);
                }
            }
            groupedByLoc.computeIfAbsent(loc, k -> new ArrayList<>()).add(app);
        }

        List<LocationPerformance> list = new ArrayList<>();
        for (Map.Entry<String, List<ApplicationRecord>> entry : groupedByLoc.entrySet()) {
            String loc = entry.getKey();
            List<ApplicationRecord> locApps = entry.getValue();
            int count = locApps.size();

            long interviews = countInterviews(locApps);
            long offers = countOffers(locApps);

            String confidence = count >= MIN_DIMENSION_SAMPLE ? "SUFFICIENT" : "INSUFFICIENT_DATA";
            double interviewRate = count >= MIN_DIMENSION_SAMPLE ? (double) interviews / count : 0.0;
            double offerRate = count >= MIN_DIMENSION_SAMPLE ? (double) offers / count : 0.0;

            list.add(LocationPerformance.builder()
                    .location(loc)
                    .applications(count)
                    .interviews((int) interviews)
                    .offers((int) offers)
                    .interviewRate(interviewRate)
                    .offerRate(offerRate)
                    .confidenceStatus(confidence)
                    .build());
        }

        return list;
    }

    public List<RemoteTypePerformance> getRemoteTypePerformance(UUID candidateId) {
        List<ApplicationRecord> apps = applicationRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId);
        Map<String, List<ApplicationRecord>> groupedByMode = new HashMap<>();

        for (ApplicationRecord app : apps) {
            String mode = "Unknown";
            if (app.getJobId() != null) {
                try {
                    Optional<DiscoveryJob> jobOpt = jobRepository.findById(app.getJobId());
                    if (jobOpt.isPresent() && jobOpt.get().getWorkMode() != null) {
                        mode = jobOpt.get().getWorkMode();
                    }
                } catch (Exception e) {
                    log.warn("Error fetching job for remote type performance: ", e);
                }
            }
            groupedByMode.computeIfAbsent(mode, k -> new ArrayList<>()).add(app);
        }

        List<RemoteTypePerformance> list = new ArrayList<>();
        for (Map.Entry<String, List<ApplicationRecord>> entry : groupedByMode.entrySet()) {
            String mode = entry.getKey();
            List<ApplicationRecord> modeApps = entry.getValue();
            int count = modeApps.size();

            long interviews = countInterviews(modeApps);
            long offers = countOffers(modeApps);

            String confidence = count >= MIN_DIMENSION_SAMPLE ? "SUFFICIENT" : "INSUFFICIENT_DATA";
            double interviewRate = count >= MIN_DIMENSION_SAMPLE ? (double) interviews / count : 0.0;
            double offerRate = count >= MIN_DIMENSION_SAMPLE ? (double) offers / count : 0.0;

            list.add(RemoteTypePerformance.builder()
                    .workMode(mode)
                    .applications(count)
                    .interviews((int) interviews)
                    .offers((int) offers)
                    .interviewRate(interviewRate)
                    .offerRate(offerRate)
                    .confidenceStatus(confidence)
                    .build());
        }

        return list;
    }

    public List<ResumePerformance> getResumeVersionPerformance(UUID candidateId) {
        List<ApplicationRecord> apps = applicationRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId);
        
        Map<String, List<ApplicationRecord>> grouped = new HashMap<>();
        for (ApplicationRecord app : apps) {
            UUID resumeId = app.getSelectedResumeId();
            Integer ver = app.getSelectedResumeVersion();

            if (resumeId == null && app.getMetadata() != null) {
                String rIdStr = String.valueOf(app.getMetadata().get("resumeId"));
                if (rIdStr != null && !"null".equals(rIdStr)) {
                    try {
                        resumeId = UUID.fromString(rIdStr);
                    } catch (Exception ignored) {}
                }
            }

            if (ver == null && app.getMetadata() != null) {
                Object verObj = app.getMetadata().get("resumeVersion");
                if (verObj != null) {
                    try {
                        ver = Integer.parseInt(String.valueOf(verObj));
                    } catch (Exception ignored) {}
                }
            }

            if (resumeId != null) {
                int finalVer = ver != null ? ver : 1;
                String key = resumeId.toString() + ":" + finalVer;
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(app);
            }
        }

        List<ResumePerformance> list = new ArrayList<>();
        for (Map.Entry<String, List<ApplicationRecord>> entry : grouped.entrySet()) {
            String[] parts = entry.getKey().split(":");
            UUID resumeId = UUID.fromString(parts[0]);
            int version = Integer.parseInt(parts[1]);
            List<ApplicationRecord> resApps = entry.getValue();
            int count = resApps.size();

            long interviews = countInterviews(resApps);
            long offers = countOffers(resApps);

            String confidence = count >= MIN_DIMENSION_SAMPLE ? "SUFFICIENT" : "INSUFFICIENT_DATA";
            double interviewRate = count >= MIN_DIMENSION_SAMPLE ? (double) interviews / count : 0.0;
            double offerRate = count >= MIN_DIMENSION_SAMPLE ? (double) offers / count : 0.0;

            String resumeTitle = "Resume v" + version;
            try {
                Optional<Resume> rOpt = resumeRepository.findActiveById(resumeId);
                if (rOpt.isPresent()) {
                    resumeTitle = rOpt.get().getTitle() + " v" + version;
                }
            } catch (Exception ignored) {}

            list.add(ResumePerformance.builder()
                    .resumeId(resumeId)
                    .resumeVersion(version)
                    .title(resumeTitle)
                    .applications(count)
                    .interviews((int) interviews)
                    .offers((int) offers)
                    .interviewRate(interviewRate)
                    .offerRate(offerRate)
                    .sampleSize(count)
                    .confidenceStatus(confidence)
                    .build());
        }

        return list;
    }

    public List<SourcePerformance> getSourcePerformance(UUID candidateId) {
        List<ApplicationRecord> apps = applicationRepository.findByCandidateIdOrderByCreatedAtDesc(candidateId);
        Map<String, List<ApplicationRecord>> groupedBySource = new HashMap<>();

        for (ApplicationRecord app : apps) {
            String source = app.getConnectorId() != null ? app.getConnectorId().toLowerCase() : "unknown";
            groupedBySource.computeIfAbsent(source, k -> new ArrayList<>()).add(app);
        }

        List<SourcePerformance> list = new ArrayList<>();
        for (Map.Entry<String, List<ApplicationRecord>> entry : groupedBySource.entrySet()) {
            String source = entry.getKey();
            List<ApplicationRecord> sourceApps = entry.getValue();
            int count = sourceApps.size();

            long interviews = countInterviews(sourceApps);
            long offers = countOffers(sourceApps);
            long submitted = countSubmitted(sourceApps);
            long verified = countVerified(sourceApps);

            String confidence = count >= MIN_DIMENSION_SAMPLE ? "SUFFICIENT" : "INSUFFICIENT_DATA";
            double interviewRate = count >= MIN_DIMENSION_SAMPLE ? (double) interviews / count : 0.0;
            double offerRate = count >= MIN_DIMENSION_SAMPLE ? (double) offers / count : 0.0;

            list.add(SourcePerformance.builder()
                    .source(source)
                    .applications(count)
                    .submitted((int) submitted)
                    .verified((int) verified)
                    .interviews((int) interviews)
                    .offers((int) offers)
                    .interviewRate(interviewRate)
                    .offerRate(offerRate)
                    .confidenceStatus(confidence)
                    .build());
        }

        return list;
    }

    private long countInterviews(List<ApplicationRecord> roleApps) {
        return roleApps.stream().filter(app -> {
            List<ApplicationHistory> history = historyRepository.findByApplicationIdOrderByCreatedAtAsc(app.getApplicationId());
            return history.stream().anyMatch(h -> h.getToState() == WorkflowState.INTERVIEW) 
                    || app.getWorkflowState() == WorkflowState.INTERVIEW;
        }).count();
    }

    private long countOffers(List<ApplicationRecord> roleApps) {
        return roleApps.stream().filter(app -> {
            List<ApplicationHistory> history = historyRepository.findByApplicationIdOrderByCreatedAtAsc(app.getApplicationId());
            return history.stream().anyMatch(h -> h.getToState() == WorkflowState.OFFER) 
                    || app.getWorkflowState() == WorkflowState.OFFER;
        }).count();
    }

    private long countRejections(List<ApplicationRecord> roleApps) {
        return roleApps.stream().filter(app -> {
            List<ApplicationHistory> history = historyRepository.findByApplicationIdOrderByCreatedAtAsc(app.getApplicationId());
            return history.stream().anyMatch(h -> h.getToState() == WorkflowState.REJECTED || h.getToState() == WorkflowState.REJECTED_BY_COMPANY) 
                    || app.getWorkflowState() == WorkflowState.REJECTED || app.getWorkflowState() == WorkflowState.REJECTED_BY_COMPANY;
        }).count();
    }

    private long countSubmitted(List<ApplicationRecord> roleApps) {
        Set<WorkflowState> submittedStates = Set.of(
                WorkflowState.SUBMITTED, WorkflowState.SUBMITTED_VERIFIED, WorkflowState.VERIFICATION_PENDING,
                WorkflowState.UNDER_REVIEW, WorkflowState.ASSESSMENT, WorkflowState.INTERVIEW,
                WorkflowState.OFFER, WorkflowState.REJECTED, WorkflowState.REJECTED_BY_COMPANY,
                WorkflowState.WITHDRAWN, WorkflowState.COMPLETED
        );
        return roleApps.stream().filter(a -> submittedStates.contains(a.getWorkflowState())).count();
    }

    private long countVerified(List<ApplicationRecord> roleApps) {
        Set<WorkflowState> verifiedStates = Set.of(
                WorkflowState.SUBMITTED_VERIFIED, WorkflowState.UNDER_REVIEW, WorkflowState.ASSESSMENT,
                WorkflowState.INTERVIEW, WorkflowState.OFFER, WorkflowState.REJECTED,
                WorkflowState.REJECTED_BY_COMPANY, WorkflowState.WITHDRAWN, WorkflowState.COMPLETED
        );
        return roleApps.stream().filter(a -> verifiedStates.contains(a.getWorkflowState())).count();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CareerOutcomeProfile {
        private UUID candidateId;
        private int totalApplications;
        private int totalApproved;
        private int totalSubmitted;
        private int totalVerified;
        private int totalInterviews;
        private int totalOffers;
        private int totalRejections;
        private double interviewRate;
        private double offerRate;
        private double responseRate;
        private double averageTimeToInterview;
        private double averageTimeToOffer;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RolePerformance {
        private String roleName;
        private int applications;
        private int submitted;
        private int verified;
        private int interviews;
        private int offers;
        private int rejections;
        private double interviewRate;
        private double offerRate;
        private String confidenceStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillPerformance {
        private String skillName;
        private int applications;
        private int interviews;
        private int offers;
        private double interviewRate;
        private double offerRate;
        private String confidenceStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanyPerformance {
        private String companyName;
        private int applications;
        private int interviews;
        private int offers;
        private int rejections;
        private double interviewRate;
        private double offerRate;
        private String confidenceStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationPerformance {
        private String location;
        private int applications;
        private int interviews;
        private int offers;
        private double interviewRate;
        private double offerRate;
        private String confidenceStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RemoteTypePerformance {
        private String workMode;
        private int applications;
        private int interviews;
        private int offers;
        private double interviewRate;
        private double offerRate;
        private String confidenceStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResumePerformance {
        private UUID resumeId;
        private int resumeVersion;
        private String title;
        private int applications;
        private int interviews;
        private int offers;
        private double interviewRate;
        private double offerRate;
        private int sampleSize;
        private String confidenceStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourcePerformance {
        private String source;
        private int applications;
        private int submitted;
        private int verified;
        private int interviews;
        private int offers;
        private double interviewRate;
        private double offerRate;
        private String confidenceStatus;
    }
}
