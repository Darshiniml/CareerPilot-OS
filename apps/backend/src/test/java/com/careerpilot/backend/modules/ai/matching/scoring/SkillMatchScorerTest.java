package com.careerpilot.backend.modules.ai.matching.scoring;

import com.careerpilot.backend.modules.ai.matching.profile.CandidateProfile;
import com.careerpilot.backend.modules.ai.matching.profile.CompanyProfile;
import com.careerpilot.backend.modules.ai.matching.profile.JobProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillMatchScorerTest {

    private SkillMatchScorer scorer;

    @BeforeEach
    void setUp() {
        scorer = new SkillMatchScorer();
    }

    @Test
    void testPerfectSkillMatch() {
        CandidateProfile candidate = CandidateProfile.builder()
                .skills(Set.of("java", "spring", "react"))
                .build();

        JobProfile job = JobProfile.builder()
                .requiredSkills(Set.of("java", "spring"))
                .preferredSkills(Set.of("react"))
                .build();

        double score = scorer.score(candidate, CompanyProfile.builder().build(), job);
        assertEquals(100.0, score, 0.01);
    }

    @Test
    void testPartialSkillMatch() {
        CandidateProfile candidate = CandidateProfile.builder()
                .skills(Set.of("java", "spring"))
                .build();

        JobProfile job = JobProfile.builder()
                .requiredSkills(Set.of("java", "spring", "react"))
                .preferredSkills(Set.of("angular"))
                .build();

        double score = scorer.score(candidate, CompanyProfile.builder().build(), job);
        assertTrue(score > 60.0 && score < 100.0);
    }

    @Test
    void testNoSkillMatch() {
        CandidateProfile candidate = CandidateProfile.builder()
                .skills(Set.of("python", "django"))
                .build();

        JobProfile job = JobProfile.builder()
                .requiredSkills(Set.of("java", "spring"))
                .build();

        double score = scorer.score(candidate, CompanyProfile.builder().build(), job);
        assertEquals(0.0, score, 0.01);
    }

    @Test
    void testEmptyJobSkills() {
        CandidateProfile candidate = CandidateProfile.builder()
                .skills(Set.of("java", "spring"))
                .build();

        JobProfile job = JobProfile.builder()
                .requiredSkills(new HashSet<>())
                .preferredSkills(new HashSet<>())
                .build();

        double score = scorer.score(candidate, CompanyProfile.builder().build(), job);
        assertEquals(100.0, score, 0.01);
    }

    @Test
    void testOnlyPreferredSkills() {
        CandidateProfile candidate = CandidateProfile.builder()
                .skills(Set.of("java", "react"))
                .build();

        JobProfile job = JobProfile.builder()
                .requiredSkills(new HashSet<>())
                .preferredSkills(Set.of("react", "angular"))
                .build();

        double score = scorer.score(candidate, CompanyProfile.builder().build(), job);
        assertTrue(score > 0.0 && score <= 100.0);
    }
}
