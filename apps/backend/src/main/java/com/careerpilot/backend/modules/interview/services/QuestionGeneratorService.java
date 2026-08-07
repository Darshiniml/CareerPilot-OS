package com.careerpilot.backend.modules.interview.services;

import com.careerpilot.backend.modules.interview.domain.InterviewKnowledge;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@Service
public class QuestionGeneratorService {

    public List<Map<String, Object>> generateQuestions(InterviewKnowledge knowledge) {
        List<Map<String, Object>> questions = new ArrayList<>();
        if (knowledge.getRequiredTechnologies().contains("java")) {
            questions.add(Map.of("category", "Technical", "question", "How would you design a REST API in Spring Boot?"));
        }
        if (knowledge.getBehavioralTopics().contains("leadership")) {
            questions.add(Map.of("category", "Behavioral", "question", "Tell me about a time you led a team through ambiguity."));
        }
        if (knowledge.getCodingTopics().contains("arrays")) {
            questions.add(Map.of("category", "Coding", "question", "How would you solve two-sum efficiently?"));
        }
        return questions;
    }
}
