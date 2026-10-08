package com.careerpilot.backend.modules.ai.matching.scoring;

import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineeringCultureMatchScorerTest {

    private EngineeringCultureMatchScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new EngineeringCultureMatchScorer();
    }

    @Test
    void testPerfectCultureMatch() {
        CandidateProfile candidate = CandidateProfile.builder()
                .preferredWorkStyle("agile collaborative innovative")
                .build();

        CompanyProfile company = CompanyProfile.builder()
                .engineeringCulture("agile collaborative innovative environment")
                .build();

        double score = scorer.score(candidate, company, JobProfile.builder().build());
        assertTrue(score >= 90.0);
    }

    @Test
    void testPartialCultureMatch() {
        CandidateProfile candidate = CandidateProfile.builder()
                .preferredWorkStyle("agile")
                .build();

        CompanyProfile company = CompanyProfile.builder()
                .engineeringCulture("agile collaborative innovative")
                .build();

        double score = scorer.score(candidate, company, JobProfile.builder().build());
        assertTrue(score >= 50.0 && score < 100.0);
    }

    @Test
    void testNoCultureMatch() {
        CandidateProfile candidate = CandidateProfile.builder()
                .preferredWorkStyle("structured hierarchical")
                .build();

        CompanyProfile company = CompanyProfile.builder()
                .engineeringCulture("agile collaborative autonomous")
                .build();

        double score = scorer.score(candidate, company, JobProfile.builder().build());
        assertTrue(score < 80.0);
    }

    @Test
    void testNoCompanyCultureSpecified() {
        CandidateProfile candidate = CandidateProfile.builder()
                .preferredWorkStyle("agile")
                .build();

        CompanyProfile company = CompanyProfile.builder()
                .engineeringCulture(null)
                .build();

        double score = scorer.score(candidate, company, JobProfile.builder().build());
        assertTrue(Double.isNaN(score), "missing company culture is not assessable, not a perfect score");
    }

    @Test
    void testNoCandidatePreferenceSpecified() {
        CandidateProfile candidate = CandidateProfile.builder()
                .preferredWorkStyle(null)
                .build();

        CompanyProfile company = CompanyProfile.builder()
                .engineeringCulture("agile collaborative")
                .build();

        double score = scorer.score(candidate, company, JobProfile.builder().build());
        assertTrue(Double.isNaN(score), "missing candidate preference is not assessable");
    }

    @Test
    void testMentorshipCultureMatch() {
        CandidateProfile candidate = CandidateProfile.builder()
                .preferredWorkStyle("mentorship growth")
                .build();

        CompanyProfile company = CompanyProfile.builder()
                .engineeringCulture("mentorship collaborative environment")
                .build();

        double score = scorer.score(candidate, company, JobProfile.builder().build());
        assertTrue(score >= 60.0);
    }
}
