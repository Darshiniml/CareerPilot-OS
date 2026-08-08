package com.careerpilot.backend.modules.analytics.services;

import com.careerpilot.backend.modules.analytics.domain.LearningPath;
import com.careerpilot.backend.modules.analytics.domain.LearningPathItem;
import com.careerpilot.backend.modules.analytics.repositories.LearningPathRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class LearningPathEngine {

    private final LearningPathRepository learningPathRepository;

    public LearningPath generateLearningPath(UUID candidateId, String targetSkill, String currentLevel, String targetLevel) {
        LearningPath path = LearningPath.builder()
                .id(UUID.randomUUID())
                .candidateId(candidateId)
                .skill(targetSkill)
                .currentLevel(currentLevel)
                .targetLevel(targetLevel)
                .priority("HIGH")
                .estimatedHours(40)
                .expectedMatchImprovement(8.5)
                .build();

        List<LearningPathItem> items = new ArrayList<>();
        String skillLower = targetSkill.toLowerCase().trim();

        if (skillLower.contains("kubernetes")) {
            items.add(LearningPathItem.builder().id(UUID.randomUUID()).learningPath(path).stepName("Linux System Administration Basics").sequenceNumber(1).prerequisiteSteps("None").build());
            items.add(LearningPathItem.builder().id(UUID.randomUUID()).learningPath(path).stepName("Docker Containers & Packaging").sequenceNumber(2).prerequisiteSteps("Linux System Administration Basics").build());
            items.add(LearningPathItem.builder().id(UUID.randomUUID()).learningPath(path).stepName("Kubernetes Core Concepts & Pod Orchestration").sequenceNumber(3).prerequisiteSteps("Docker Containers & Packaging").build());
            items.add(LearningPathItem.builder().id(UUID.randomUUID()).learningPath(path).stepName("Kubernetes Advanced Networking & Helm Charts").sequenceNumber(4).prerequisiteSteps("Kubernetes Core Concepts & Pod Orchestration").build());
            path.setEstimatedHours(60);
            path.setExpectedMatchImprovement(10.0);
        } else if (skillLower.contains("docker")) {
            items.add(LearningPathItem.builder().id(UUID.randomUUID()).learningPath(path).stepName("Linux Basics & Command Line").sequenceNumber(1).prerequisiteSteps("None").build());
            items.add(LearningPathItem.builder().id(UUID.randomUUID()).learningPath(path).stepName("Docker Image Creation & Dockerfile").sequenceNumber(2).prerequisiteSteps("Linux Basics & Command Line").build());
            items.add(LearningPathItem.builder().id(UUID.randomUUID()).learningPath(path).stepName("Docker Compose & Container Networking").sequenceNumber(3).prerequisiteSteps("Docker Image Creation & Dockerfile").build());
            path.setEstimatedHours(30);
            path.setExpectedMatchImprovement(6.0);
        } else if (skillLower.contains("aws") || skillLower.contains("cloud")) {
            items.add(LearningPathItem.builder().id(UUID.randomUUID()).learningPath(path).stepName("Networking Foundations (DNS, IP)").sequenceNumber(1).prerequisiteSteps("None").build());
            items.add(LearningPathItem.builder().id(UUID.randomUUID()).learningPath(path).stepName("AWS IAM & VPC Core Security").sequenceNumber(2).prerequisiteSteps("Networking Foundations (DNS, IP)").build());
            items.add(LearningPathItem.builder().id(UUID.randomUUID()).learningPath(path).stepName("AWS Compute (EC2, ECS, Fargate)").sequenceNumber(3).prerequisiteSteps("AWS IAM & VPC Core Security").build());
            items.add(LearningPathItem.builder().id(UUID.randomUUID()).learningPath(path).stepName("AWS Serverless & Database Services (S3, RDS, Lambda)").sequenceNumber(4).prerequisiteSteps("AWS Compute (EC2, ECS, Fargate)").build());
            path.setEstimatedHours(50);
            path.setExpectedMatchImprovement(9.0);
        } else if (skillLower.contains("spring") || skillLower.contains("java")) {
            items.add(LearningPathItem.builder().id(UUID.randomUUID()).learningPath(path).stepName("Java Programming & OOP Basics").sequenceNumber(1).prerequisiteSteps("None").build());
            items.add(LearningPathItem.builder().id(UUID.randomUUID()).learningPath(path).stepName("Java Collection Framework & Concurrency").sequenceNumber(2).prerequisiteSteps("Java Programming & OOP Basics").build());
            items.add(LearningPathItem.builder().id(UUID.randomUUID()).learningPath(path).stepName("Spring Core & Dependency Injection").sequenceNumber(3).prerequisiteSteps("Java Collection Framework & Concurrency").build());
            items.add(LearningPathItem.builder().id(UUID.randomUUID()).learningPath(path).stepName("Spring Boot REST APIs & Spring Data JPA").sequenceNumber(4).prerequisiteSteps("Spring Core & Dependency Injection").build());
            path.setEstimatedHours(80);
            path.setExpectedMatchImprovement(12.0);
        } else if (skillLower.contains("sql") || skillLower.contains("database")) {
            items.add(LearningPathItem.builder().id(UUID.randomUUID()).learningPath(path).stepName("Relational Database Concepts").sequenceNumber(1).prerequisiteSteps("None").build());
            items.add(LearningPathItem.builder().id(UUID.randomUUID()).learningPath(path).stepName("Basic SQL queries (SELECT, JOIN, WHERE)").sequenceNumber(2).prerequisiteSteps("Relational Database Concepts").build());
            items.add(LearningPathItem.builder().id(UUID.randomUUID()).learningPath(path).stepName("Advanced SQL (Indexes, Window Functions, Transactions)").sequenceNumber(3).prerequisiteSteps("Basic SQL queries (SELECT, JOIN, WHERE)").build());
            path.setEstimatedHours(25);
            path.setExpectedMatchImprovement(7.0);
        } else {
            // Default sequential path
            items.add(LearningPathItem.builder().id(UUID.randomUUID()).learningPath(path).stepName("Foundational theory of " + targetSkill).sequenceNumber(1).prerequisiteSteps("None").build());
            items.add(LearningPathItem.builder().id(UUID.randomUUID()).learningPath(path).stepName("Practical building block labs").sequenceNumber(2).prerequisiteSteps("Foundational theory of " + targetSkill).build());
            items.add(LearningPathItem.builder().id(UUID.randomUUID()).learningPath(path).stepName("Intermediate integration concepts").sequenceNumber(3).prerequisiteSteps("Practical building block labs").build());
        }

        path.setLearningSequence(items);
        return learningPathRepository.save(path);
    }
}
