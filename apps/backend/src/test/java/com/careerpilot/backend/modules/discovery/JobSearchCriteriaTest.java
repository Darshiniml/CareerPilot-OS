package com.careerpilot.backend.modules.discovery;

import com.careerpilot.backend.modules.discovery.domain.JobSearchCriteria;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JobSearchCriteriaTest {

    @Test
    void testJobSearchCriteriaBuilder() {
        JobSearchCriteria criteria = JobSearchCriteria.builder()
                .keywords(List.of("Java", "Spring Boot"))
                .preferredRoles(List.of("Backend Developer"))
                .skills(List.of("Java", "SQL"))
                .locations(List.of("Bangalore", "Remote"))
                .remoteOnly(true)
                .build();

        assertThat(criteria.getKeywords()).contains("Java");
        assertThat(criteria.isRemoteOnly()).isTrue();
        assertThat(criteria.getLocations()).contains("Bangalore");
    }
}
