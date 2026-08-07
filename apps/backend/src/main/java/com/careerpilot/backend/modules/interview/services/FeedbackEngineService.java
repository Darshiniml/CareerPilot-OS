package com.careerpilot.backend.modules.interview.services;

import com.careerpilot.backend.modules.interview.domain.InterviewQuestion;
import com.careerpilot.backend.modules.interview.domain.InterviewSession;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FeedbackEngineService {

    public Map<String, Object> buildFeedback(InterviewSession session) {
        Map<String, Object> feedback = new LinkedHashMap<>();
        feedback.put("strengths", List.of("Structured answers", "Relevant examples"));
        feedback.put("weaknesses", List.of("Need more depth on system design"));
        feedback.put("missingConcepts", List.of("Caching", "Concurrency"));
        feedback.put("suggestedImprovements", List.of("Review distributed systems basics"));
        feedback.put("learningResources", List.of("Spring Boot docs", "System Design Primer"));
        feedback.put("confidenceScore", 0.72);
        feedback.put("explainable", true);
        return feedback;
    }
}
