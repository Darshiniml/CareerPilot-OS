package com.careerpilot.backend.modules.application.services;

import com.careerpilot.backend.modules.resume.domain.Resume;
import com.careerpilot.backend.modules.resume.domain.ResumeVersion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeSelectionEngineTest {

    @Test
    void selectsHighestScoringExistingResumeVersion() {
        ResumeSelectionEngine engine = new ResumeSelectionEngine();
        Resume resume = Resume.builder().id(UUID.randomUUID()).build();
        ResumeVersion versionTwo = ResumeVersion.builder().id(UUID.randomUUID()).versionNumber(2).build();
        ResumeVersion versionOne = ResumeVersion.builder().id(UUID.randomUUID()).versionNumber(1).build();
        ResumeVersion versionThree = ResumeVersion.builder().id(UUID.randomUUID()).versionNumber(3).build();

        ResumeSelectionEngine.Selection selection = engine.select(
                List.of(
                        new ResumeSelectionEngine.Candidate(resume, versionOne, 0.82, Set.of("java"), "backend"),
                        new ResumeSelectionEngine.Candidate(resume, versionTwo, 0.90, Set.of("java", "spring"), "backend"),
                        new ResumeSelectionEngine.Candidate(resume, versionThree, 0.88, Set.of("kotlin"), "backend")
                ),
                Set.of("java", "spring"),
                "backend"
        );

        assertThat(selection.resumeId()).isEqualTo(resume.getId());
        assertThat(selection.version()).isEqualTo(2);
    }
}
