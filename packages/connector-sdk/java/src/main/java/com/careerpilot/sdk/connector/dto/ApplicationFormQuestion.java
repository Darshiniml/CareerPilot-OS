package com.careerpilot.sdk.connector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationFormQuestion {
    private String id;
    private String label;
    private String type; // e.g., text, select, file, boolean
    private boolean required;
    private List<String> options;
}
