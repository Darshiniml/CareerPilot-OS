package com.careerpilot.backend.modules.ai.matching.scoring;

import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;
import com.careerpilot.backend.modules.analytics.services.HistoricalSuccessSignalService;
import com.careerpilot.backend.modules.discovery.domain.DiscoveryJob;
import com.careerpilot.backend.modules.discovery.repositories.DiscoveryJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class HistoricalSuccessScorer implements MatchScorer {

    private final HistoricalSuccessSignalService historicalSuccessSignalService;
    private final DiscoveryJobRepository discoveryJobRepository;

    @Override
    public String getFactorName() {
        return "historicalSuccess";
    }

    @Override
    public double score(CandidateProfile candidate, CompanyProfile company, JobProfile job) {
        UUID candidateId = candidate.getCandidateId();
        if (candidateId == null) {
            return NOT_ASSESSABLE;
        }

        // Fetch DiscoveryJob to get title, company name, location, and workMode
        String title = null;
        String companyName = null;
        String location = null;
        String workMode = null;
        String source = null;

        if (job.getJobId() != null) {
            try {
                Optional<DiscoveryJob> discJobOpt = discoveryJobRepository.findById(job.getJobId());
                if (discJobOpt.isPresent()) {
                    DiscoveryJob dj = discJobOpt.get();
                    title = dj.getTitle();
                    companyName = dj.getCompany() != null ? dj.getCompany() : dj.getNormalizedCompany();
                    location = dj.getLocation();
                    workMode = dj.getWorkMode();
                    source = dj.getConnectorId() != null ? dj.getConnectorId() : dj.getSource();
                }
            } catch (Exception e) {
                log.warn("Error fetching job in HistoricalSuccessScorer: ", e);
            }
        }

        Set<String> skills = new HashSet<>();
        if (job.getRequiredSkills() != null) skills.addAll(job.getRequiredSkills());
        if (job.getPreferredSkills() != null) skills.addAll(job.getPreferredSkills());

        HistoricalSuccessSignalService.HistoricalSuccessSignal signal = historicalSuccessSignalService.getHistoricalSignal(
                candidateId,
                title,
                companyName,
                location,
                workMode,
                source,
                skills,
                null,
                null
        );

        if (!signal.isAvailable()) {
            return NOT_ASSESSABLE; // no history is not evidence of failure
        }

        return signal.getHistoricalSuccessScore();
    }

}
