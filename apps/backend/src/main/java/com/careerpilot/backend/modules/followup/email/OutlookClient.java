package com.careerpilot.backend.modules.followup.email;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Outlook / Microsoft 365 via the Microsoft identity platform and Microsoft Graph (Mail.Send). */
@Component
public class OutlookClient implements EmailProviderClient {

    static final String SCOPES = "offline_access User.Read Mail.Send";
    private static final String AUTHORITY = "https://login.microsoftonline.com/common/oauth2/v2.0";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public OutlookClient(@Value("${careerpilot.email.outlook.client-id:}") String clientId,
                         @Value("${careerpilot.email.outlook.client-secret:}") String clientSecret,
                         @Value("${careerpilot.email.outlook.redirect-uri:}") String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    @Override
    public String provider() {
        return "OUTLOOK";
    }

    @Override
    public boolean isConfigured() {
        return !clientId.isBlank() && !clientSecret.isBlank() && !redirectUri.isBlank();
    }

    @Override
    public String authorizationUrl(String state) {
        return AUTHORITY + "/authorize?" + OAuthHttp.form(Map.of(
                "client_id", clientId, "redirect_uri", redirectUri, "response_type", "code",
                "response_mode", "query", "scope", SCOPES, "state", state));
    }

    @Override
    public TokenGrant exchangeCode(String code) {
        JsonNode token = OAuthHttp.postForm(AUTHORITY + "/token", Map.of(
                "client_id", clientId, "client_secret", clientSecret, "code", code,
                "redirect_uri", redirectUri, "grant_type", "authorization_code", "scope", SCOPES), "Outlook");
        String access = token.path("access_token").asText();
        JsonNode me = OAuthHttp.get("https://graph.microsoft.com/v1.0/me", access, "Outlook");
        String email = me.hasNonNull("mail") ? me.get("mail").asText() : me.path("userPrincipalName").asText(null);
        return new TokenGrant(token.path("refresh_token").asText(), email, token.path("scope").asText(SCOPES));
    }

    @Override
    public String accessToken(String refreshToken) {
        JsonNode token = OAuthHttp.postForm(AUTHORITY + "/token", Map.of(
                "client_id", clientId, "client_secret", clientSecret, "refresh_token", refreshToken,
                "grant_type", "refresh_token", "scope", SCOPES), "Outlook");
        return token.path("access_token").asText();
    }

    @Override
    public String send(String accessToken, String to, String subject, String body) {
        Map<String, Object> message = Map.of(
                "subject", subject,
                "body", Map.of("contentType", "Text", "content", body),
                "toRecipients", List.of(Map.of("emailAddress", Map.of("address", to))));
        OAuthHttp.postJson("https://graph.microsoft.com/v1.0/me/sendMail", accessToken,
                Map.of("message", message, "saveToSentItems", true), "Outlook");
        return null; // Graph sendMail returns 202 without a message id
    }
}
