package com.careerpilot.backend.modules.followup.email;

import com.careerpilot.backend.config.CodedException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/** Gmail via Google OAuth 2.0 and the Gmail API (send-only scope). */
@Component
public class GmailClient implements EmailProviderClient {

    static final String SCOPES = "openid email https://www.googleapis.com/auth/gmail.send";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public GmailClient(@Value("${careerpilot.email.gmail.client-id:}") String clientId,
                       @Value("${careerpilot.email.gmail.client-secret:}") String clientSecret,
                       @Value("${careerpilot.email.gmail.redirect-uri:}") String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    @Override
    public String provider() {
        return "GMAIL";
    }

    @Override
    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank() && !redirectUri.isBlank();
    }

    @Override
    public String authorizationUrl(String state) {
        return "https://accounts.google.com/o/oauth2/v2/auth?" + OAuthHttp.form(Map.of(
                "client_id", clientId, "redirect_uri", redirectUri, "response_type", "code", "scope", SCOPES,
                "access_type", "offline", "prompt", "consent", "include_granted_scopes", "true", "state", state));
    }

    @Override
    public TokenGrant exchangeCode(String code) {
        JsonNode token = OAuthHttp.postForm("https://oauth2.googleapis.com/token", Map.of(
                "code", code, "client_id", clientId, "client_secret", clientSecret,
                "redirect_uri", redirectUri, "grant_type", "authorization_code"), "Gmail");
        if (!token.hasNonNull("refresh_token")) {
            throw new CodedException("Google did not return a refresh token; remove CareerPilot's access in your Google "
                    + "account and connect again", "EMAIL_AUTH_FAILED", 502, null);
        }
        String email = token.hasNonNull("id_token") ? OAuthHttp.jwtClaim(token.get("id_token").asText(), "email") : null;
        return new TokenGrant(token.get("refresh_token").asText(), email, token.path("scope").asText(SCOPES));
    }

    @Override
    public String accessToken(String refreshToken) {
        JsonNode token = OAuthHttp.postForm("https://oauth2.googleapis.com/token", Map.of(
                "refresh_token", refreshToken, "client_id", clientId, "client_secret", clientSecret,
                "grant_type", "refresh_token"), "Gmail");
        return token.path("access_token").asText();
    }

    @Override
    public String send(String accessToken, String to, String subject, String body) {
        String raw = MimeMessages.plainText(to, subject, body);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        JsonNode result = OAuthHttp.postJson("https://gmail.googleapis.com/gmail/v1/users/me/messages/send",
                accessToken, Map.of("raw", encoded), "Gmail");
        return result.path("id").asText(null);
    }
}
