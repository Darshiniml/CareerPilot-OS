package com.careerpilot.backend.modules.followup.email;

import com.careerpilot.backend.config.CodedException;
import com.careerpilot.backend.modules.followup.domain.EmailConnection;
import com.careerpilot.backend.modules.followup.repositories.EmailConnectionRepository;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailSecurityTest {

    private static String key() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return Base64.getEncoder().encodeToString(k);
    }

    @Test
    void refreshTokensAreEncryptedAndTamperEvident() {
        TokenCipher cipher = new TokenCipher(key());
        String encrypted = cipher.encrypt("1//refresh-token-secret");
        assertFalse(encrypted.contains("refresh-token-secret"));
        assertEquals("1//refresh-token-secret", cipher.decrypt(encrypted));
        byte[] raw = Base64.getDecoder().decode(encrypted);
        raw[raw.length - 1] ^= 1;
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(Base64.getEncoder().encodeToString(raw)));
    }

    @Test
    void withoutAKeyConnectionsAreDisabledNotPlaintext() {
        TokenCipher cipher = new TokenCipher("");
        CodedException ex = assertThrows(CodedException.class, () -> cipher.encrypt("token"));
        assertEquals("EMAIL_NOT_CONFIGURED", ex.getCode());
    }

    private static EmailConnectionService service(TokenCipher cipher, EmailProviderClient client, Instant now,
                                                  EmailConnectionRepository repo) {
        return new EmailConnectionService(List.of(client), repo, cipher, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static EmailProviderClient fakeGmail() {
        EmailProviderClient client = mock(EmailProviderClient.class);
        when(client.provider()).thenReturn("GMAIL");
        when(client.isConfigured()).thenReturn(true);
        when(client.authorizationUrl(any())).thenAnswer(i -> "https://accounts.google.com/auth?state=" + i.getArgument(0));
        when(client.exchangeCode("good-code")).thenReturn(new EmailProviderClient.TokenGrant("refresh", "me@gmail.com", "send"));
        return client;
    }

    private static String state(String url) {
        return URLDecoder.decode(URI.create(url).getQuery().replace("state=", ""), StandardCharsets.UTF_8);
    }

    @Test
    void oauthCallbackStoresEncryptedTokenForTheSignedUser() {
        TokenCipher cipher = new TokenCipher(key());
        EmailConnectionRepository repo = mock(EmailConnectionRepository.class);
        when(repo.findByUserIdAndProvider(any(), any())).thenReturn(Optional.empty());
        Instant now = Instant.parse("2026-10-08T10:00:00Z");
        EmailConnectionService service = service(cipher, fakeGmail(), now, repo);
        UUID userId = UUID.randomUUID();

        String st = state(service.authorizationUrl(userId, "gmail"));
        assertEquals(userId, service.completeAuthorization("gmail", "good-code", st));

        verify(repo).save(argThat((EmailConnection c) -> c.getUserId().equals(userId)
                && !c.getEncryptedRefreshToken().equals("refresh")
                && "refresh".equals(cipher.decrypt(c.getEncryptedRefreshToken()))));
    }

    @Test
    void forgedOrExpiredStateIsRejected() {
        TokenCipher cipher = new TokenCipher(key());
        EmailConnectionRepository repo = mock(EmailConnectionRepository.class);
        Instant now = Instant.parse("2026-10-08T10:00:00Z");
        EmailProviderClient gmail = fakeGmail();
        String st = state(service(cipher, gmail, now, repo).authorizationUrl(UUID.randomUUID(), "gmail"));

        // Forged: swap the user id but keep the signature.
        String[] parts = st.split("\\.", 2);
        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String forgedPayload = UUID.randomUUID() + payload.substring(payload.indexOf('|'));
        String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(forgedPayload.getBytes(StandardCharsets.UTF_8)) + "." + parts[1];
        assertThrows(SecurityException.class, () -> service(cipher, gmail, now, repo).completeAuthorization("gmail", "good-code", forged));

        // Expired: 11 minutes later.
        EmailConnectionService later = service(cipher, gmail, now.plusSeconds(660), repo);
        assertThrows(IllegalArgumentException.class, () -> later.completeAuthorization("gmail", "good-code", st));
        verify(repo, never()).save(any());
    }

    @Test
    void headerInjectionIsBlocked() {
        assertThrows(IllegalArgumentException.class,
                () -> MimeMessages.plainText("a@b.c\r\nBcc: victim@x.y", "Hi", "body"));
        assertThrows(IllegalArgumentException.class,
                () -> MimeMessages.plainText("a@b.c", "Hi\nBcc: victim@x.y", "body"));
        String msg = MimeMessages.plainText("a@b.c", "Hello", "Line1\nLine2");
        assertTrue(msg.startsWith("To: a@b.c\r\n"));
    }
}
