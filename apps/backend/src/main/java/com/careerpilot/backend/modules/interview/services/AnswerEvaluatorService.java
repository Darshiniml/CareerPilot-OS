package com.careerpilot.backend.modules.interview.services;

import com.careerpilot.backend.modules.interview.domain.InterviewQuestion;
import org.springframework.stereotype.Service;

@Service
public class AnswerEvaluatorService {

    public InterviewQuestion evaluate(InterviewQuestion question) {
        double correctness = question.getCorrectnessScore() == null ? 0.0 : question.getCorrectnessScore();
        double completeness = question.getCompletenessScore() == null ? 0.0 : question.getCompletenessScore();
        double technicalAccuracy = question.getTechnicalAccuracyScore() == null ? 0.0 : question.getTechnicalAccuracyScore();
        double communication = question.getCommunicationScore() == null ? 0.0 : question.getCommunicationScore();
        double examples = question.getExamplesScore() == null ? 0.0 : question.getExamplesScore();
        double confidence = question.getConfidenceScore() == null ? 0.0 : question.getConfidenceScore();
        double structure = question.getStructureScore() == null ? 0.0 : question.getStructureScore();

        question.setEvaluationFeedback(String.format(
                "Correctness %.0f%%, completeness %.0f%%, technical accuracy %.0f%%, communication %.0f%%, examples %.0f%%, confidence %.0f%%, structure %.0f%%",
                correctness * 100,
                completeness * 100,
                technicalAccuracy * 100,
                communication * 100,
                examples * 100,
                confidence * 100,
                structure * 100
        ));
        return question;
    }
}
