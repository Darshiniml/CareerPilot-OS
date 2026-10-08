package com.careerpilot.backend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                "default-very-secure-secret-key-that-is-at-least-256-bits-long-careerpilot-os-2026",
                60000,  // 1 minute access token
                120000  // 2 minutes refresh token
        );

        userDetails = new User(
                "test@example.com",
                "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }

    @Test
    void testGenerateAccessTokenAndExtractUsername() {
        String token = jwtTokenProvider.generateAccessToken(userDetails);
        assertNotNull(token);
        assertEquals("test@example.com", jwtTokenProvider.extractUsername(token));
    }

    @Test
    void testValidateToken_Success() {
        String token = jwtTokenProvider.generateAccessToken(userDetails);
        assertTrue(jwtTokenProvider.validateToken(token, userDetails));
    }

    @Test
    void testValidateToken_Expired() throws InterruptedException {
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(
                "default-very-secure-secret-key-that-is-at-least-256-bits-long-careerpilot-os-2026",
                1,
                1
        );
        String token = shortLivedProvider.generateAccessToken(userDetails);
        Thread.sleep(10);
        assertFalse(shortLivedProvider.validateToken(token, userDetails));
    }

    @Test
    void refreshTokenIsNotAcceptedAsAccessToken() {
        String refresh = jwtTokenProvider.generateRefreshToken(userDetails);
        assertFalse(jwtTokenProvider.validateToken(refresh, userDetails));
        assertTrue(jwtTokenProvider.isValidRefreshToken(refresh));
    }

    @Test
    void accessTokenIsNotAcceptedAsRefreshToken() {
        String access = jwtTokenProvider.generateAccessToken(userDetails);
        assertFalse(jwtTokenProvider.isValidRefreshToken(access));
        assertFalse(jwtTokenProvider.isValidRefreshToken("not-a-jwt"));
    }
}
