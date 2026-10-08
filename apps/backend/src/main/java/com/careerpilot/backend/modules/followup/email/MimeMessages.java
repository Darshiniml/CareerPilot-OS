package com.careerpilot.backend.modules.followup.email;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Minimal RFC 5322 plain-text message builder with header-injection protection. */
final class MimeMessages {

    private MimeMessages() {
    }

    static String plainText(String to, String subject, String body) {
        requireSingleLine(to, "recipient");
        requireSingleLine(subject, "subject");
        String encodedSubject = "=?UTF-8?B?" + Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8)) + "?=";
        return "To: " + to + "\r\n"
                + "Subject: " + encodedSubject + "\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: base64\r\n\r\n"
                + Base64.getMimeEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8));
    }

    static void requireSingleLine(String value, String field) {
        if (value == null || value.contains("\r") || value.contains("\n")) {
            throw new IllegalArgumentException("Invalid " + field + ": line breaks are not allowed");
        }
    }
}
