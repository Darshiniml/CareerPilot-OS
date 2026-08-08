package com.careerpilot.backend.modules.analytics.events;

import com.careerpilot.backend.modules.analytics.services.CareerAnalyticsService;
import com.careerpilot.shared.events.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsEventListener {

    private final CareerAnalyticsService analyticsService;

    @EventListener
    @Async
    public void onMatchCompleted(MatchCompletedEvent event) {
        log.info("Received MatchCompletedEvent for candidate {}", event.getCandidateId());
        try {
            analyticsService.calculateAndPersistAnalytics(event.getCandidateId(), "MONTHLY");
        } catch (Exception e) {
            log.error("Failed to process MatchCompletedEvent", e);
        }
    }

    @EventListener
    @Async
    public void onApplicationCompleted(ApplicationCompletedEvent event) {
        log.info("Received ApplicationCompletedEvent for candidate {}", event.getUserId());
        try {
            analyticsService.calculateAndPersistAnalytics(event.getUserId(), "MONTHLY");
        } catch (Exception e) {
            log.error("Failed to process ApplicationCompletedEvent", e);
        }
    }

    @EventListener
    @Async
    public void onInterviewCompleted(InterviewCompletedEvent event) {
        log.info("Received InterviewCompletedEvent for candidate {}", event.getUserId());
        try {
            analyticsService.calculateAndPersistAnalytics(event.getUserId(), "MONTHLY");
        } catch (Exception e) {
            log.error("Failed to process InterviewCompletedEvent", e);
        }
    }

    @EventListener
    @Async
    public void onCareerHealthUpdated(CareerHealthUpdatedEvent event) {
        log.info("Received CareerHealthUpdatedEvent for candidate {}", event.getUserId());
        try {
            analyticsService.calculateAndPersistAnalytics(event.getUserId(), "MONTHLY");
        } catch (Exception e) {
            log.error("Failed to process CareerHealthUpdatedEvent", e);
        }
    }
}
