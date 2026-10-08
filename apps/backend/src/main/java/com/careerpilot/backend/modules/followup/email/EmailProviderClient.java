package com.careerpilot.backend.modules.followup.email;

/** OAuth + send operations for one mailbox provider (Gmail or Outlook). */
public interface EmailProviderClient {

    /** GMAIL or OUTLOOK. */
    String provider();

    /** True when client id/secret/redirect URI are configured. */
    boolean isConfigured();

    String authorizationUrl(String state);

    /** Exchange an authorization code; returns the refresh token and the mailbox address. */
    TokenGrant exchangeCode(String code);

    /** Obtain a short-lived access token from a refresh token. */
    String accessToken(String refreshToken);

    /** Send a plain-text email; returns the provider message id when the provider reports one. */
    String send(String accessToken, String to, String subject, String body);

    record TokenGrant(String refreshToken, String emailAddress, String scopes) {
    }
}
