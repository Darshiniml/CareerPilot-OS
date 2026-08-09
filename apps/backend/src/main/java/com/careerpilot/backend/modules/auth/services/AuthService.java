package com.careerpilot.backend.modules.auth.services;

import com.careerpilot.backend.config.JwtTokenProvider;
import com.careerpilot.backend.modules.auth.domain.*;
import com.careerpilot.backend.modules.auth.providers.EmailPasswordProvider;
import com.careerpilot.shared.dto.auth.*;
import com.careerpilot.shared.events.UserLoggedInEvent;
import com.careerpilot.sdk.auth.EmailPasswordAuthRequest;
import com.careerpilot.sdk.auth.AuthResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final EmailPasswordProvider emailPasswordProvider;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserPreferenceRepository userPreferenceRepository,
            EmailPasswordProvider emailPasswordProvider,
            JwtTokenProvider jwtTokenProvider,
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.emailPasswordProvider = emailPasswordProvider;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public UserDto register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already in use");
        }

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> {
                    Role newRole = Role.builder()
                            .id(UUID.randomUUID())
                            .name("ROLE_USER")
                            .build();
                    return roleRepository.save(newRole);
                });

        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .roles(Set.of(userRole))
                .build();

        UserPreference preference = UserPreference.builder()
                .id(UUID.randomUUID())
                .user(user)
                .theme("dark")
                .emailNotifications(true)
                .autoApply(false)
                .build();

        user.setPreference(preference);
        User savedUser = userRepository.save(user);

        return mapToUserDto(savedUser);
    }

    public LoginResponse login(LoginRequest request) {
        EmailPasswordAuthRequest authRequest = EmailPasswordAuthRequest.builder()
                .email(request.getEmail())
                .password(request.getPassword())
                .build();

        AuthResponse authResponse = emailPasswordProvider.authenticate(authRequest);

        if (!authResponse.isAuthenticated()) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        String accessToken = jwtTokenProvider.generateAccessToken(userDetails);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

        UserDto userDto = UserDto.builder()
                .id(authResponse.getUserId())
                .email(authResponse.getEmail())
                .firstName(authResponse.getFirstName())
                .lastName(authResponse.getLastName())
                .roles(authResponse.getRoles())
                .build();

        // Publish UserLoggedInEvent to trigger asynchronous background job discovery
        try {
            eventPublisher.publishEvent(UserLoggedInEvent.builder()
                    .eventId(UUID.randomUUID())
                    .timestamp(Instant.now())
                    .userId(authResponse.getUserId())
                    .email(authResponse.getEmail())
                    .build());
        } catch (Exception ignored) {}

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresInSeconds(jwtTokenProvider.getAccessTokenExpirationInSeconds())
                .user(userDto)
                .build();
    }

    public LoginResponse refresh(TokenRefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        if (jwtTokenProvider.isTokenExpired(refreshToken)) {
            throw new IllegalArgumentException("Refresh token has expired");
        }

        String email = jwtTokenProvider.extractUsername(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token user"));

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        String accessToken = jwtTokenProvider.generateAccessToken(userDetails);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(newRefreshToken)
                .expiresInSeconds(jwtTokenProvider.getAccessTokenExpirationInSeconds())
                .user(mapToUserDto(user))
                .build();
    }

    private UserDto mapToUserDto(User user) {
        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList());

        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(roles)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
