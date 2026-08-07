package com.careerpilot.shared.dto.profile;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreferencesDto {
    private String workStyle; // REMOTE, HYBRID, ONSITE, FLEXIBLE
    
    @Min(value = 0, message = "Minimum salary cannot be negative")
    private Integer salaryMin;
    
    @Min(value = 0, message = "Maximum salary cannot be negative")
    private Integer salaryMax;
    
    private String currencyCode; // ISO 4217 currency
    private String salaryPeriod; // YEARLY, MONTHLY, HOURLY
    private String employmentType; // FULL_TIME, PART_TIME, CONTRACT, INTERN, FREELANCE, TEMPORARY
    private boolean jobAlertSettings;
    
    private List<String> preferredRoles;
    private List<String> preferredLocations;
    private List<String> preferredCompanies;
}
