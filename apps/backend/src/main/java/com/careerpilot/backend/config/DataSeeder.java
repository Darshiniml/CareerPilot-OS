package com.careerpilot.backend.config;

import com.careerpilot.backend.modules.auth.domain.Role;
import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.RoleRepository;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * Startup seeding.
 *
 * <ul>
 *   <li>Always ensures the {@code ROLE_USER} role exists (required for registration).</li>
 *   <li>Optionally creates a demo account, only when {@code careerpilot.demo.enabled=true} and a
 *       password is supplied via {@code CAREERPILOT_DEMO_PASSWORD}. The demo account is created once
 *       and its password is never reset afterwards, so seeding cannot act as a backdoor.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private static final String ROLE_USER = "ROLE_USER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${careerpilot.demo.enabled:false}")
    private boolean demoEnabled;

    @Value("${careerpilot.demo.email:demo@careerpilot.local}")
    private String demoEmail;

    @Value("${careerpilot.demo.password:}")
    private String demoPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Role userRole = roleRepository.findByName(ROLE_USER)
                .orElseGet(() -> {
                    Role r = new Role();
                    r.setId(UUID.randomUUID());
                    r.setName(ROLE_USER);
                    return roleRepository.save(r);
                });

        if (!demoEnabled) {
            return;
        }
        if (demoPassword == null || demoPassword.length() < 10) {
            log.warn("DataSeeder: demo account requested but CAREERPILOT_DEMO_PASSWORD is missing or shorter than 10 characters; skipping");
            return;
        }
        if (userRepository.findByEmail(demoEmail).isPresent()) {
            return; // never modify an existing account
        }
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(demoEmail)
                .passwordHash(passwordEncoder.encode(demoPassword))
                .firstName("Demo")
                .lastName("User")
                .roles(Set.of(userRole))
                .build();
        userRepository.save(user);
        log.info("DataSeeder: created demo account {}", demoEmail);
    }
}
