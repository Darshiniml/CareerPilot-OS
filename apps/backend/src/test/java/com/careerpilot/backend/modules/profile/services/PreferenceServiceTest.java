package com.careerpilot.backend.modules.profile.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreferenceServiceTest {

    @Test
    void preservesCanonicalEmploymentTypeValues() {
        assertThat(PreferenceService.normalizeEmploymentType("FULL_TIME")).isEqualTo("FULL_TIME");
        assertThat(PreferenceService.normalizeEmploymentType("PART_TIME")).isEqualTo("PART_TIME");
        assertThat(PreferenceService.normalizeEmploymentType("CONTRACT")).isEqualTo("CONTRACT");
        assertThat(PreferenceService.normalizeEmploymentType("TEMPORARY")).isEqualTo("TEMPORARY");
    }

    @Test
    void mapsTheDocumentedInternshipAliasToThePersistedEnum() {
        assertThat(PreferenceService.normalizeEmploymentType("INTERNSHIP")).isEqualTo("INTERN");
        assertThat(PreferenceService.normalizeEmploymentType("INTERN")).isEqualTo("INTERN");
    }

    @Test
    void rejectsInvalidOrEmptyEmploymentTypesAndLeavesNullUnset() {
        assertThat(PreferenceService.normalizeEmploymentType(null)).isNull();
        assertThatThrownBy(() -> PreferenceService.normalizeEmploymentType(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PreferenceService.normalizeEmploymentType("FULLTIME"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
