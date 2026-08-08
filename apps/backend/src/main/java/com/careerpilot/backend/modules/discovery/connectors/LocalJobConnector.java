package com.careerpilot.backend.modules.discovery.connectors;

import com.careerpilot.connector.sdk.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class LocalJobConnector implements Connector {

    private boolean enabled = true;

    @Override
    public String getConnectorId() {
        return "local-jobs";
    }

    @Override
    public String getConnectorType() {
        return "LOCAL";
    }

    @Override
    public String getDisplayName() {
        return "Local Job Discovery Connector";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean authenticate() {
        return true;
    }

    @Override
    public List<DiscoveredJob> discoverJobs(DiscoveryContext context) {
        List<DiscoveredJob> jobs = new ArrayList<>();
        
        jobs.add(DiscoveredJob.builder()
                .externalId("job-local-001")
                .source("local-jobs")
                .sourceUrl("https://careers.techcorp.example/jobs/001")
                .title("Senior Software Engineer - AI & Backend")
                .company("TechCorp AI")
                .location("Remote")
                .employmentType("FULL_TIME")
                .workMode("REMOTE")
                .salary("$150,000 - $180,000")
                .postedDate(LocalDateTime.now().minusDays(1))
                .rawContent("We are seeking a Senior Software Engineer with strong Java, Spring Boot, AI, and Microservices experience to build scalable platforms.")
                .skills(List.of("java", "spring boot", "ai", "microservices", "postgresql"))
                .build());

        jobs.add(DiscoveredJob.builder()
                .externalId("job-local-002")
                .source("local-jobs")
                .sourceUrl("https://careers.innovate.example/jobs/002")
                .title("AI Systems Architect")
                .company("Innovate Labs")
                .location("New York, NY")
                .employmentType("FULL_TIME")
                .workMode("HYBRID")
                .salary("$170,000 - $210,000")
                .postedDate(LocalDateTime.now().minusDays(2))
                .rawContent("Innovate Labs is hiring an AI Systems Architect to lead LLM integration, agentic workflows, and vector databases.")
                .skills(List.of("python", "llm", "ai", "qdrant", "vector databases"))
                .build());

        jobs.add(DiscoveredJob.builder()
                .externalId("job-local-003")
                .source("local-jobs")
                .sourceUrl("https://careers.clouddata.example/jobs/003")
                .title("Full Stack Developer (React & Java)")
                .company("CloudData Solutions")
                .location("San Francisco, CA")
                .employmentType("FULL_TIME")
                .workMode("HYBRID")
                .salary("$140,000 - $165,000")
                .postedDate(LocalDateTime.now().minusDays(3))
                .rawContent("Join CloudData Solutions as a Full Stack Developer. Build beautiful dashboards with React, TypeScript, Vite, and Spring Boot APIs.")
                .skills(List.of("react", "typescript", "java", "spring boot", "vite"))
                .build());

        return jobs;
    }

    @Override
    public DiscoveredJob normalizeJob(Object rawJob) {
        if (rawJob instanceof DiscoveredJob dj) {
            return dj;
        }
        return null;
    }

    @Override
    public ConnectorHealth healthCheck() {
        return ConnectorHealth.builder()
                .connectorId(getConnectorId())
                .status(ConnectorHealthStatus.HEALTHY)
                .lastSynchronization(Instant.now())
                .lastSuccess(Instant.now())
                .responseTimeMs(10)
                .failureCount(0)
                .message("Local job connector operational.")
                .build();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
