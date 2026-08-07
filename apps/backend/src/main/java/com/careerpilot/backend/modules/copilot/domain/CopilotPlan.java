package com.careerpilot.backend.modules.copilot.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CopilotPlan {
    private String planType;
    private List<String> tasks = new ArrayList<>();
}
