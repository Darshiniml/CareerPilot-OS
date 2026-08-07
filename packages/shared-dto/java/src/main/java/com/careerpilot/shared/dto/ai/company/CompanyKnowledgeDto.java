package com.careerpilot.shared.dto.ai.company;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyKnowledgeDto {
    private UUID companyId;
    private ExtractedValueDto<String> companyName;
    private ExtractedValueDto<String> legalName;
    private ExtractedValueDto<List<String>> aliases;
    private ExtractedValueDto<String> website;
    private ExtractedValueDto<String> headquarters;
    private ExtractedValueDto<List<String>> offices;
    private ExtractedValueDto<Integer> foundedYear;
    private ExtractedValueDto<String> employeeRange;
    private ExtractedValueDto<String> fundingStage;
    private ExtractedValueDto<String> ownershipType; // Public, Private, Startup, Government
    private ExtractedValueDto<List<String>> industries;
    private ExtractedValueDto<List<String>> products;
    private ExtractedValueDto<List<String>> services;
    private ExtractedValueDto<String> engineeringCulture;
    private ExtractedValueDto<List<String>> technologyStack;
    private ExtractedValueDto<List<String>> hiringSignals;
    private ExtractedValueDto<List<String>> benefits;
    private ExtractedValueDto<List<String>> certifications;
    private ExtractedValueDto<Map<String, String>> socialProfiles;
    private Map<String, Object> additionalProperties; // for future schema-less expansion
}
