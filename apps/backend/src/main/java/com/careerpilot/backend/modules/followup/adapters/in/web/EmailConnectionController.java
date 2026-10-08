package com.careerpilot.backend.modules.followup.adapters.in.web;

import com.careerpilot.backend.config.CurrentUser;
import com.careerpilot.backend.modules.followup.email.EmailConnectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.Map;

/** Gmail / Outlook mailbox connections via OAuth (send-only scopes). */
@RestController
@RequestMapping("/api/v1/email")
@RequiredArgsConstructor
@Slf4j
public class EmailConnectionController {

    private final EmailConnectionService connectionService;
    private final CurrentUser currentUser;

    @Value("${careerpilot.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @GetMapping("/connections")
    public ResponseEntity<List<Map<String, Object>>> connections(Principal principal) {
        return ResponseEntity.ok(connectionService.status(currentUser.requireId(principal)));
    }

    @PostMapping("/connections/{provider}/authorize")
    public ResponseEntity<Map<String, String>> authorize(@PathVariable String provider, Principal principal) {
        return ResponseEntity.ok(Map.of("authorizationUrl",
                connectionService.authorizationUrl(currentUser.requireId(principal), provider)));
    }

    @DeleteMapping("/connections/{provider}")
    public ResponseEntity<Void> disconnect(@PathVariable String provider, Principal principal) {
        connectionService.disconnect(currentUser.requireId(principal), provider);
        return ResponseEntity.noContent().build();
    }

    /**
     * Provider redirect target. The browser arrives here without the app's bearer token, so the user
     * is identified solely by the signed, expiring OAuth state.
     */
    @GetMapping("/oauth/{provider}/callback")
    public ResponseEntity<Void> callback(@PathVariable String provider,
                                         @RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        String result;
        if (error != null) {
            result = "denied";
        } else {
            try {
                connectionService.completeAuthorization(provider, code, state);
                result = "connected";
            } catch (RuntimeException e) {
                log.warn("OAuth callback for {} failed: {}", provider, e.getMessage());
                result = "failed";
            }
        }
        URI target = URI.create(frontendUrl + "/settings?email=" + URLEncoder.encode(result, StandardCharsets.UTF_8)
                + "&provider=" + URLEncoder.encode(provider, StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(target);
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
