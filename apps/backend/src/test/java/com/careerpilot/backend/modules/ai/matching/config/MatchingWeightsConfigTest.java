package com.careerpilot.backend.modules.ai.matching.config;

import com.careerpilot.shared.dto.ai.matching.MatchWeightsDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchingWeightsConfigTest {

    private MatchingWeightsConfig config;

    @BeforeEach
    void setUp() {
        config = new MatchingWeightsConfig();
    }

    @Test
    void testDefaultWeights() {
        MatchWeightsDto weights = config.getCurrentWeights();

        assertNotNull(weights);
        assertEquals(0.30, weights.getSkillMatch(), 0.01);
        assertEquals(0.20, weights.getExperienceMatch(), 0.01);
        assertEquals(0.10, weights.getProjectMatch(), 0.01);
        assertEquals(0.15, weights.getTechnologyMatch(), 0.01);
    }

    @Test
    void testSetCustomWeights() {
        MatchWeightsDto customWeights = MatchWeightsDto.builder()
                .skillMatch(0.40)
                .experienceMatch(0.30)
                .build();

        config.setCustomWeights(customWeights);
        MatchWeightsDto current = config.getCurrentWeights();

        assertEquals(0.40, current.getSkillMatch(), 0.01);
        assertEquals(0.30, current.getExperienceMatch(), 0.01);
    }

    @Test
    void testResetToDefaults() {
        MatchWeightsDto customWeights = MatchWeightsDto.builder()
                .skillMatch(0.40)
                .experienceMatch(0.30)
                .build();

        config.setCustomWeights(customWeights);
        config.resetToDefaults();

        MatchWeightsDto current = config.getCurrentWeights();
        assertEquals(0.30, current.getSkillMatch(), 0.01);
        assertEquals(0.20, current.getExperienceMatch(), 0.01);
    }

    @Test
    void testValidateWeightsValid() {
        MatchWeightsDto validWeights = MatchWeightsDto.builder()
                .skillMatch(0.30)
                .experienceMatch(0.20)
                .projectMatch(0.10)
                .technologyMatch(0.15)
                .locationMatch(0.05)
                .salaryMatch(0.05)
                .cultureMatch(0.05)
                .educationMatch(0.05)
                .certificationMatch(0.05)
                .build();

        config.validateWeights(validWeights);
    }

    @Test
    void testValidateWeightsInvalidSum() {
        MatchWeightsDto invalidWeights = MatchWeightsDto.builder()
                .skillMatch(0.50)
                .experienceMatch(0.50)
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            config.validateWeights(invalidWeights);
        });
    }

    @Test
    void testGetWeight() {
        double skillWeight = config.getWeight("skillMatch");
        assertEquals(0.30, skillWeight, 0.01);

        double nonExistentWeight = config.getWeight("nonExistent");
        assertEquals(0.0, nonExistentWeight, 0.01);
    }

    @Test
    void testGetAllWeights() {
        var allWeights = config.getAllWeights();

        assertNotNull(allWeights);
        assertTrue(allWeights.containsKey("skillMatch"));
        assertTrue(allWeights.containsKey("experienceMatch"));
        assertTrue(allWeights.size() >= 14);
    }
}
