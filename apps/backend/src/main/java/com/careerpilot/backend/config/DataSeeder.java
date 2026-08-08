package com.careerpilot.backend.config;

import com.careerpilot.backend.modules.auth.domain.Role;
import com.careerpilot.backend.modules.auth.domain.RoleRepository;
import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Seeds a default test user on every startup if not already present.
 * This prevents the "Invalid email or password" error after backend restarts.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String TEST_EMAIL    = "darshini.test@careerpilot.com";
    private static final String TEST_PASSWORD = "Test@12345";
    private static final String TEST_FIRST    = "Darshini";
    private static final String TEST_LAST     = "Test";
    private static final String ROLE_USER     = "ROLE_USER";

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Ensure ROLE_USER exists
        Role userRole = roleRepository.findByName(ROLE_USER)
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setId(UUID.randomUUID());
                    r.setName(ROLE_USER);
                    return roleRepository.save(r);
                });

        // Create test user if missing
        if (userRepository.findByEmail(TEST_EMAIL).isEmpty()) {
            User user = User.builder()
                    .id(UUID.randomUUID())
                    .email(TEST_EMAIL)
                    .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                    .firstName(TEST_FIRST)
                    .lastName(TEST_LAST)
                    .roles(Set.of(userRole))
                    .build();
            userRepository.save(user);
            log.info("DataSeeder: Created test user → {}", TEST_EMAIL);
        } else {
            log.info("DataSeeder: Test user already exists → {}", TEST_EMAIL);
        }
    }
}
