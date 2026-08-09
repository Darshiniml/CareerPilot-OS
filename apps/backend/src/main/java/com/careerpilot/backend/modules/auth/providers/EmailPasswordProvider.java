package com.careerpilot.backend.modules.auth.providers;

import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.sdk.auth.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class EmailPasswordProvider implements AuthenticationProvider {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public EmailPasswordProvider(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public AuthResponse authenticate(AuthRequest request) {
        if (!(request instanceof EmailPasswordAuthRequest emailPasswordRequest)) {
            throw new IllegalArgumentException("Unsupported AuthRequest type. Expected EmailPasswordAuthRequest.");
        }

        User user = userRepository.findByEmail(emailPasswordRequest.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        boolean matches = passwordEncoder.matches(emailPasswordRequest.getPassword(), user.getPasswordHash());

        return AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(user.getRoles().stream().map(role -> role.getName()).collect(Collectors.toList()))
                .authenticated(matches)
                .build();
    }

    @Override
    public boolean supports(AuthMethod method) {
        return AuthMethod.EMAIL_PASSWORD.equals(method);
    }
}
