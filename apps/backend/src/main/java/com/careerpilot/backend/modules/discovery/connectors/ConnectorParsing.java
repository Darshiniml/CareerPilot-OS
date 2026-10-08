package com.careerpilot.backend.modules.discovery.connectors;

import com.careerpilot.backend.modules.ai.web.SafeWebPageFetcher;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Helpers that map connector payloads to job fields WITHOUT inventing values: anything the source
 * does not provide comes back as {@code null}.
 */
final class ConnectorParsing {

    static final int MAX_CONTENT = 12000;

    private ConnectorParsing() {
    }

    static String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        String v = node.get(field).asText().trim();
        return v.isEmpty() ? null : v;
    }

    /** HTML (possibly entity-encoded, as Greenhouse returns it) to plain text. */
    static String plainText(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String decoded = html.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&amp;", "&");
        String text = SafeWebPageFetcher.htmlToText(decoded).trim();
        return text.length() > MAX_CONTENT ? text.substring(0, MAX_CONTENT) : text;
    }

    static LocalDateTime isoDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (Exception ignored) {
            // try other ISO forms below
        }
        try {
            return LocalDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC);
        } catch (Exception ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    static LocalDateTime epochMillis(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field) || !node.get(field).canConvertToLong()) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(node.get(field).asLong()), ZoneOffset.UTC);
    }

    static LocalDateTime rfc1123(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                    .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (Exception e) {
            return null;
        }
    }

    /** Work mode only when the source states it (explicit field or the word "remote"/"hybrid" in location). */
    static String workMode(String explicit, String location) {
        if (explicit != null) {
            String e = explicit.toLowerCase(Locale.ROOT);
            if (e.contains("remote")) return "REMOTE";
            if (e.contains("hybrid")) return "HYBRID";
            if (e.contains("onsite") || e.contains("on-site") || e.contains("in office") || e.contains("on site")) return "ON_SITE";
        }
        if (location != null) {
            String l = location.toLowerCase(Locale.ROOT);
            if (l.contains("remote")) return "REMOTE";
            if (l.contains("hybrid")) return "HYBRID";
        }
        return null;
    }

    static String employmentType(String raw) {
        if (raw == null) {
            return null;
        }
        String r = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return switch (r) {
            case "fulltime", "permanent" -> "FULL_TIME";
            case "parttime" -> "PART_TIME";
            case "contract", "contractor", "freelance", "temporary", "temp" -> "CONTRACT";
            case "intern", "internship" -> "INTERN";
            default -> null;
        };
    }

    /** Stable id for sources without one: SHA-256 of the canonical link (never random). */
    static String stableId(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 12);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String capitalize(String token) {
        return token == null || token.isBlank() ? null : token.substring(0, 1).toUpperCase(Locale.ROOT) + token.substring(1);
    }
}
