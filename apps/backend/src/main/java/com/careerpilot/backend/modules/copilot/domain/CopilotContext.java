package com.careerpilot.backend.modules.copilot.domain;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class CopilotContext {
    private CopilotIntent intent;
    private String userId;
    private Map<String, Object> resumeKnowledge = new HashMap<>();
    private Map<String, Object> companyKnowledge = new HashMap<>();
    private Map<String, Object> jobKnowledge = new HashMap<>();
    private Map<String, Object> matchingData = new HashMap<>();
    private Map<String, Object> applicationData = new HashMap<>();
    private Map<String, Object> interviewData = new HashMap<>();
    private Map<String, Object> learningData = new HashMap<>();
    private Map<String, Object> preferences = new HashMap<>();
    private Map<String, Object> retrievedKnowledge = new HashMap<>();

    public Map<String, Object> getResumeKnowledge() { return resumeKnowledge; }
    public void setResumeKnowledge(Map<String, Object> resumeKnowledge) { this.resumeKnowledge = resumeKnowledge; }
    public Map<String, Object> getCompanyKnowledge() { return companyKnowledge; }
    public void setCompanyKnowledge(Map<String, Object> companyKnowledge) { this.companyKnowledge = companyKnowledge; }
    public Map<String, Object> getJobKnowledge() { return jobKnowledge; }
    public void setJobKnowledge(Map<String, Object> jobKnowledge) { this.jobKnowledge = jobKnowledge; }
    public Map<String, Object> getMatchingData() { return matchingData; }
    public void setMatchingData(Map<String, Object> matchingData) { this.matchingData = matchingData; }
    public Map<String, Object> getApplicationData() { return applicationData; }
    public void setApplicationData(Map<String, Object> applicationData) { this.applicationData = applicationData; }
    public Map<String, Object> getInterviewData() { return interviewData; }
    public void setInterviewData(Map<String, Object> interviewData) { this.interviewData = interviewData; }
    public Map<String, Object> getLearningData() { return learningData; }
    public void setLearningData(Map<String, Object> learningData) { this.learningData = learningData; }
    public Map<String, Object> getPreferences() { return preferences; }
    public void setPreferences(Map<String, Object> preferences) { this.preferences = preferences; }
    public Map<String, Object> getRetrievedKnowledge() { return retrievedKnowledge; }
    public void setRetrievedKnowledge(Map<String, Object> retrievedKnowledge) { this.retrievedKnowledge = retrievedKnowledge; }
}
