package com.careerpilot.backend.modules.copilot.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CopilotRecommendation {
    private String title;
    private String reason;
    private List<String> evidence = new ArrayList<>();
    private String action;
}
