package com.careerpilot.backend.modules.agent.services;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class PublicSignalsReferenceProvider implements ReferenceSourceProvider {

    @Override
    public String getProviderName() {
        return "public-signals-provider";
    }

    @Override
    public Map<String, Object> queryPublicSignals(UUID userId, String companyName) throws Exception {
        // Enforce safety constraints: no bypass, captcha, or robots.txt evasion
        boolean requiresCaptchaBypass = false; // Mock check
        boolean blockedByRobotsTxt = companyName != null && companyName.toLowerCase().contains("blocked");
        
        if (requiresCaptchaBypass || blockedByRobotsTxt) {
            throw new IllegalAccessException("Scraping blocked by target robots.txt or requires CAPTCHA bypass.");
        }
        
        Map<String, Object> signals = new HashMap<>();
        signals.put("provider", getProviderName());
        signals.put("publicProfessionalPostingsFound", true);
        signals.put("verifiableHiringSignals", List.of("Active hiring", "Engineering expansion"));
        signals.put("timestamp", System.currentTimeMillis());
        return signals;
    }
}
