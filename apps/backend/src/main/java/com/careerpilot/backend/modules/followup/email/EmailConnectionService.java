package com.careerpilot.backend.modules.followup.email;

import com.careerpilot.backend.config.CodedException;
import com.careerpilot.backend.modules.followup.domain.EmailConnection;
import com.careerpilot.backend.modules.followup.repositories.EmailConnectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * OAuth mailbox connections. The OAuth {@code state} is signed and bound to the user, provider and
 * a 10-minute expiry, so the (unauthenticated) browser callback cannot be replayed or forged.
 * Refresh tokens are stored encrypted; access tokens are never stored.
 */
@Service
public class EmailConnectionService {

    static final long STATE_TTL_SECONDS = 600;

    private final Map<String, EmailProviderClient> providers = new HashMap<>();
    private final EmailConnectionRepository connectionRepository;
    private final TokenCipher cipher;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public EmailConnectionService(List<EmailProviderClient> clients, EmailConnectionRepository connectionRepository,
                                  TokenCipher cipher, Clock clock) {
        clients.forEach(c -> providers.put(c.provider(), c));
        this.connectionRepository = connectionRepository;
        this.cipher = cipher;
        this.clock = clock;
    }

    public List<Map<String, Object>> status(UUID userId) {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, EmailConnection> connections = new HashMap<>();
        connectionRepository.findByUserId(userId).forEach(c -> connections.put(c.getProvider(), c));
        for (EmailProviderClient client : providers.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("provider", client.provider());
            row.put("providerConfigured", client.isConfigured() && cipher.isConfigured());
            EmailConnection c = connections.get(client.provider());
            row.put("connected", c != null && EmailConnection.CONNECTED.equals(c.getStatus()));
            row.put("emailAddress", c != null ? c.getEmailAddress() : null);
            row.put("connectedAt", c != null ? c.getConnectedAt() : null);
            result.add(row);
        }
        result.sort(Comparator.comparing(r -> String.valueOf(r.get("provider"))));
        return result;
    }

    public String authorizationUrl(UUID userId, String provider) {
        EmailProviderClient client = client(provider);
        long expires = clock.instant().getEpochSecond() + STATE_TTL_SECONDS;
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        String payload = userId + "|" + client.provider() + "|" + expires + "|" + Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "." + cipher.sign(payload);
        return client.authorizationUrl(state);
    }

    /** Handle the provider redirect. Returns the user id the connection was stored for. */
    @Transactional
    public UUID completeAuthorization(String provider, String code, String state) {
        EmailProviderClient client = client(provider);
        if (code == null || code.isBlank() || state == null || !state.contains(".")) {
            throw new IllegalArgumentException("Invalid authorisation response");
        }
        String[] parts = state.split("\\.", 2);
        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        if (!cipher.verify(payload, parts[1])) {
            throw new SecurityException("Invalid OAuth state");
        }
        String[] fields = payload.split("\\|");
        if (fields.length != 4 || !fields[1].equals(client.provider())) {
            throw new SecurityException("OAuth state does not match the provider");
        }
        if (Long.parseLong(fields[2]) < clock.instant().getEpochSecond()) {
            throw new IllegalArgumentException("The authorisation request expired; please try again");
        }
        UUID userId = UUID.fromString(fields[0]);
        EmailProviderClient.TokenGrant grant = client.exchangeCode(code);
        Instant now = clock.instant();
        EmailConnection connection = connectionRepository.findByUserIdAndProvider(userId, client.provider())
                .orElseGet(() -> EmailConnection.builder().id(UUID.randomUUID()).userId(userId)
                        .provider(client.provider()).connectedAt(now).build());
        connection.setEncryptedRefreshToken(cipher.encrypt(grant.refreshToken()));
        connection.setEmailAddress(grant.emailAddress());
        connection.setScopes(grant.scopes());
        connection.setStatus(EmailConnection.CONNECTED);
        connection.setConnectedAt(now);
        connection.setUpdatedAt(now);
        connectionRepository.save(connection);
        return userId;
    }

    @Transactional
    public void disconnect(UUID userId, String provider) {
        connectionRepository.findByUserIdAndProvider(userId, provider.toUpperCase(Locale.ROOT))
                .ifPresent(connectionRepository::delete);
    }

    /** A fresh access token for the user's connected mailbox. */
    public String accessToken(EmailConnection connection) {
        return client(connection.getProvider()).accessToken(cipher.decrypt(connection.getEncryptedRefreshToken()));
    }

    public EmailProviderClient client(String provider) {
        EmailProviderClient client = providers.get(provider == null ? "" : provider.toUpperCase(Locale.ROOT));
        if (client == null) {
            throw new IllegalArgumentException("Unknown email provider: " + provider);
        }
        if (!client.isConfigured()) {
            throw new CodedException(client.provider() + " OAuth is not configured (client id/secret/redirect URI)",
                    "EMAIL_PROVIDER_NOT_CONFIGURED", 503, null);
        }
        return client;
    }
}
