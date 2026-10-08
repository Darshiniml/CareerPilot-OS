package com.careerpilot.backend.modules.ai.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Fetches a public web page's text for job/company analysis, with SSRF protection:
 * only http/https, every hop's host must resolve to public addresses (no loopback, private,
 * link-local, multicast or cloud-metadata ranges), redirects are followed manually and re-checked,
 * responses are size- and time-limited, and only HTML/text content is accepted.
 */
@Component
@Slf4j
public class SafeWebPageFetcher {

    private static final int MAX_REDIRECTS = 3;
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(12);

    private static final Pattern SCRIPT_STYLE = Pattern.compile("(?is)<(script|style|noscript|svg)[^>]*>.*?</\\1>");
    private static final Pattern BLOCK_TAGS = Pattern.compile("(?i)</?(p|div|br|li|ul|ol|h[1-6]|tr|section|article|header|footer)[^>]*>");
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\x0B\\f\\r]+");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n\\s*\\n+");

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(TIMEOUT)
            .build();

    public record FetchedPage(String finalUrl, String title, String text) {
    }

    public FetchedPage fetch(String url) {
        URI uri = parse(url);
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            assertPublic(uri);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(TIMEOUT)
                    .header("User-Agent", "CareerPilot/1.0 (+job research for the account owner)")
                    .header("Accept", "text/html,text/plain;q=0.9")
                    .GET().build();
            HttpResponse<InputStream> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not fetch the page: " + e.getClass().getSimpleName());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while fetching the page");
            }
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("location")
                        .orElseThrow(() -> new IllegalArgumentException("Redirect without location"));
                uri = uri.resolve(location);
                closeQuietly(response.body());
                continue;
            }
            if (status != 200) {
                closeQuietly(response.body());
                throw new IllegalArgumentException("The page returned HTTP " + status);
            }
            String contentType = response.headers().firstValue("content-type").orElse("").toLowerCase(Locale.ROOT);
            if (!contentType.contains("text/html") && !contentType.contains("text/plain")) {
                closeQuietly(response.body());
                throw new IllegalArgumentException("Unsupported content type: " + contentType);
            }
            String body = readLimited(response.body());
            String title = extractTitle(body);
            String text = contentType.contains("text/html") ? htmlToText(body) : body;
            return new FetchedPage(uri.toString(), title, text.trim());
        }
        throw new IllegalArgumentException("Too many redirects");
    }

    static URI parse(String url) {
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw new IllegalArgumentException("Only http and https URLs are allowed");
            }
            if (uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Invalid URL host");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL");
        }
    }

    static void assertPublic(URI uri) {
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (isNonPublic(address)) {
                    throw new IllegalArgumentException("URL resolves to a non-public address");
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Unknown host: " + uri.getHost());
        }
    }

    static boolean isNonPublic(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (b.length == 4) {
            int first = b[0] & 0xff;
            int second = b[1] & 0xff;
            return first == 0 || first >= 224                     // reserved / multicast / broadcast
                    || (first == 100 && second >= 64 && second <= 127) // carrier-grade NAT
                    || (first == 169 && second == 254)                // link-local / cloud metadata
                    || (first == 198 && (second == 18 || second == 19));
        }
        // IPv6 unique-local fc00::/7
        return (b[0] & 0xfe) == 0xfc;
    }

    public static String htmlToText(String html) {
        String text = SCRIPT_STYLE.matcher(html).replaceAll(" ");
        text = BLOCK_TAGS.matcher(text).replaceAll("\n");
        text = TAGS.matcher(text).replaceAll(" ");
        text = text.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&rsquo;", "'").replace("&ndash;", "-")
                .replace("&mdash;", "-");
        text = SPACES.matcher(text).replaceAll(" ");
        return BLANK_LINES.matcher(text).replaceAll("\n\n");
    }

    private static String extractTitle(String html) {
        var m = Pattern.compile("(?is)<title[^>]*>(.*?)</title>").matcher(html);
        return m.find() ? htmlToText(m.group(1)).trim() : null;
    }

    private static String readLimited(InputStream in) {
        try (in) {
            byte[] data = in.readNBytes(MAX_BYTES + 1);
            if (data.length > MAX_BYTES) {
                throw new IllegalArgumentException("The page is larger than 2 MB");
            }
            return new String(data, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the page");
        }
    }

    private static void closeQuietly(InputStream in) {
        try {
            in.close();
        } catch (IOException ignored) {
            // nothing to do
        }
    }
}
