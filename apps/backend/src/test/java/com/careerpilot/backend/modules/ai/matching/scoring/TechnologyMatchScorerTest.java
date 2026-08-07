package com.careerpilot.backend.modules.ai.matching.scoring;

import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TechnologyMatchScorerTest {

    private TechnologyMatchScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new TechnologyMatchScorer();
    }

    @Test
    void testPerfectTechnologyMatch() {
        CandidateProfile candidate = CandidateProfile.builder()
                .technologies(Set.of("java", "spring", "react", "postgresql"))
                .build();

        JobProfile job = JobProfile.builder()
                .technologyStack(Set.of("java", "spring", "react"))
                .build();

        double score = scorer.score(candidate, CompanyProfile.builder().build(), job);
        assertEquals(100.0, score, 0.01);
    }

    @Test
    void testPartialTechnologyMatch() {
        CandidateProfile candidate = CandidateProfile.builder()
                .technologies(Set.of("java", "spring"))
                .build();

        JobProfile job = JobProfile.builder()
                .technologyStack(Set.of("java", "spring", "react", "kubernetes"))
                .build();

        double score = scorer.score(candidate, CompanyProfile.builder().build(), job);
        assertTrue(score > 40.0 && score < 100.0);
    }

    @Test
    void testCompanyTechnologyStackMatch() {
        CandidateProfile candidate = CandidateProfile.builder()
                .technologies(Set.of("java", "spring"))
                .build();

        CompanyProfile company = CompanyProfile.builder()
                .technologyStack(Set.of("java", "spring", "microservices"))
                .build();

        JobProfile job = JobProfile.builder()
                .technologyStack(Set.of())
                .build();

        double score = scorer.score(candidate, company, job);
        assertTrue(score > 50.0 && score <= 100.0);
    }

    @Test
    void testNoTechnologyMatch() {
        CandidateProfile candidate = CandidateProfile.builder()
                .technologies(Set.of("python", "django"))
                .build();

        JobProfile job = JobProfile.builder()
                .technologyStack(Set.of("java", "spring"))
                .build();

        double score = scorer.score(candidate, CompanyProfile.builder().build(), job);
        assertEquals(0.0, score, 0.01);
    }

    @Test
    void testEmptyTechnologyStacks() {
        CandidateProfile candidate = CandidateProfile.builder()
                .technologies(Set.of())
                .build();

        JobProfile job = JobProfile.builder()
                .technologyStack(Set.of())
                .build();

        double score = scorer.score(candidate, CompanyProfile.builder().build(), job);
        assertEquals(100.0, score, 0.01);
    }
}
