package com.careerpilot.backend.modules.ai.matching.profile;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileNormalizationUtilsTest {

    @Test
    void keepsNonAsciiLetters() {
        assertThat(ProfileNormalizationUtils.normalizeToken("Négociation")).isEqualTo("négociation");
        assertThat(ProfileNormalizationUtils.normalizeToken("C++ / C#")).isEqualTo("c++ c#");
    }

    @Test
    void singleLetterOrPrefixSkillsDoNotMatchBySubstring() {
        Set<String> candidate = Set.of("spring boot", "javascript", "postgresql");
        assertThat(ProfileNormalizationUtils.missingItems(candidate, Set.of("r", "java", "sql")))
                .containsExactlyInAnyOrder("r", "java", "sql");
    }

    @Test
    void wholeWordsAndPhrasesMatch() {
        Set<String> candidate = Set.of("spring boot", "java", "c++");
        assertThat(ProfileNormalizationUtils.matchedItems(candidate, Set.of("spring", "java", "c++", "c")))
                .containsExactlyInAnyOrder("spring", "java", "c++");
    }
}
