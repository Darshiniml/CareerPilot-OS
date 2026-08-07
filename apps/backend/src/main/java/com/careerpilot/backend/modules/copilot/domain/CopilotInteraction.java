package com.careerpilot.backend.modules.copilot.domain;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class CopilotInteraction {
    private String sessionId;
    private String userId;
    private String intent;
    private Map<String, Object> retrievedContext = new HashMap<>();
    private List<String> actionsExecuted = new ArrayList<>();
    private List<String> recommendations = new ArrayList<>();
    private Instant timestamp = Instant.now();
}
