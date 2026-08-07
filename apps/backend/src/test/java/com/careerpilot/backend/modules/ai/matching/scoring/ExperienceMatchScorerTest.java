package com.careerpilot.backend.modules.ai.matching.scoring;

import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperienceMatchScorerTest {

    private ExperienceMatchScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new ExperienceMatchScorer();
    }

    @Test
    void testPerfectExperienceMatch() {
        CandidateProfile candidate = CandidateProfile.builder()
                .totalExperienceYears(5.0)
                .seniorityLevel("SENIOR")
                .build();

        JobProfile job = JobProfile.builder()
                .seniority("SENIOR")
                .build();

        double score = scorer.score(candidate, CompanyProfile.builder().build(), job);
        assertEquals(100.0, score, 0.01);
    }

    @Test
    void testUnderqualifiedCandidate() {
        CandidateProfile candidate = CandidateProfile.builder()
                .totalExperienceYears(2.0)
                .seniorityLevel("JUNIOR")
                .build();

        JobProfile job = JobProfile.builder()
                .seniority("SENIOR")
                .build();

        double score = scorer.score(candidate, CompanyProfile.builder().build(), job);
        assertTrue(score < 100.0 && score >= 0.0);
    }

    @Test
    void testOverqualifiedCandidate() {
        CandidateProfile candidate = CandidateProfile.builder()
                .totalExperienceYears(10.0)
                .seniorityLevel("PRINCIPAL")
                .build();

        JobProfile job = JobProfile.builder()
                .seniority("SENIOR")
                .build();

        double score = scorer.score(candidate, CompanyProfile.builder().build(), job);
        assertTrue(score < 100.0 && score >= 70.0);
    }

    @Test
    void testFreshGraduateForEntryLevel() {
        CandidateProfile candidate = CandidateProfile.builder()
                .totalExperienceYears(0.5)
                .seniorityLevel("FRESHER")
                .build();

        JobProfile job = JobProfile.builder()
                .seniority("JUNIOR")
                .build();

        double score = scorer.score(candidate, CompanyProfile.builder().build(), job);
        assertTrue(score >= 50.0 && score <= 100.0);
    }

    @Test
    void testNoJobSenioritySpecified() {
        CandidateProfile candidate = CandidateProfile.builder()
                .totalExperienceYears(5.0)
                .seniorityLevel("SENIOR")
                .build();

        JobProfile job = JobProfile.builder()
                .seniority(null)
                .build();

        double score = scorer.score(candidate, CompanyProfile.builder().build(), job);
        assertEquals(100.0, score, 0.01);
    }
}
