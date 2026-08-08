package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.backend.modules.copilot.domain.*;
import com.careerpilot.shared.events.CopilotActionExecutedEvent;
import com.careerpilot.shared.events.CopilotPlanGeneratedEvent;
import com.careerpilot.shared.events.CopilotRecommendationGeneratedEvent;
import com.careerpilot.shared.events.CareerHealthUpdatedEvent;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CareerCopilotEngine {
    private final IntentRouter intentRouter;
    private final ContextBuilder contextBuilder;
    private final KnowledgeRetriever knowledgeRetriever;
    private final PlanningEngine planningEngine;
    private final RecommendationEngine recommendationEngine;
    private final ExplanationEngine explanationEngine;
    private final ConversationMemory conversationMemory;
    private final DashboardSummaryService dashboardSummaryService;
    private final CareerHealthScoreService careerHealthScoreService;
    private final ActionOrchestrator actionOrchestrator;
    private final ResponseAssembler responseAssembler;
    private final CopilotEventPublisher eventPublisher;

    public CareerCopilotEngine(IntentRouter intentRouter,
                               ContextBuilder contextBuilder,
                               KnowledgeRetriever knowledgeRetriever,
                               PlanningEngine planningEngine,
                               RecommendationEngine recommendationEngine,
                               ExplanationEngine explanationEngine,
                               ConversationMemory conversationMemory,
                               DashboardSummaryService dashboardSummaryService,
                               CareerHealthScoreService careerHealthScoreService,
                               ActionOrchestrator actionOrchestrator,
                               ResponseAssembler responseAssembler,
                               CopilotEventPublisher eventPublisher) {
        this.intentRouter = intentRouter;
        this.contextBuilder = contextBuilder;
        this.knowledgeRetriever = knowledgeRetriever;
        this.planningEngine = planningEngine;
        this.recommendationEngine = recommendationEngine;
        this.explanationEngine = explanationEngine;
        this.conversationMemory = conversationMemory;
        this.dashboardSummaryService = dashboardSummaryService;
        this.careerHealthScoreService = careerHealthScoreService;
        this.actionOrchestrator = actionOrchestrator;
        this.responseAssembler = responseAssembler;
        this.eventPublisher = eventPublisher;
    }

    public CopilotContext process(String request, String userId) {
        CopilotIntent intent = intentRouter.classify(request);
        CopilotContext context = contextBuilder.build(intent, null, null, null);
        context.setUserId(userId);
        context.getRetrievedKnowledge().put("query", request != null ? request : "");
        knowledgeRetriever.retrieve(context, intent);
        String action = actionOrchestrator.execute(context);
        List<CopilotRecommendation> recs = recommendationEngine.generate(context);
        CopilotPlan plan = planningEngine.createPlan(context);
        CopilotInteraction interaction = new CopilotInteraction();
        interaction.setSessionId(UUID.randomUUID().toString());
        interaction.setUserId(userId);
        interaction.setIntent(intent.name());
        interaction.setRetrievedContext(context.getRetrievedKnowledge());
        interaction.getActionsExecuted().add(action);
        interaction.setRecommendations(recs.stream().map(CopilotRecommendation::getTitle).toList());
        conversationMemory.record(interaction);
        eventPublisher.publish(CopilotPlanGeneratedEvent.builder().eventId(eventPublisher.nextId()).timestamp(eventPublisher.timestamp()).correlationId(UUID.randomUUID()).userId(UUID.fromString(userId == null ? "00000000-0000-0000-0000-000000000000" : userId)).planType(plan.getPlanType()).build());
        eventPublisher.publish(CopilotRecommendationGeneratedEvent.builder().eventId(eventPublisher.nextId()).timestamp(eventPublisher.timestamp()).correlationId(UUID.randomUUID()).userId(UUID.fromString(userId == null ? "00000000-0000-0000-0000-000000000000" : userId)).title(recs.isEmpty() ? "No recommendation" : recs.get(0).getTitle()).build());
        eventPublisher.publish(CopilotActionExecutedEvent.builder().eventId(eventPublisher.nextId()).timestamp(eventPublisher.timestamp()).correlationId(UUID.randomUUID()).userId(UUID.fromString(userId == null ? "00000000-0000-0000-0000-000000000000" : userId)).action(action).build());
        eventPublisher.publish(CareerHealthUpdatedEvent.builder().eventId(eventPublisher.nextId()).timestamp(eventPublisher.timestamp()).correlationId(UUID.randomUUID()).userId(UUID.fromString(userId == null ? "00000000-0000-0000-0000-000000000000" : userId)).overallScore(0.75).build());
        return context;
    }

    public CopilotPlan createPlan(CopilotContext context) {
        return planningEngine.createPlan(context);
    }

    public List<CopilotRecommendation> recommend(CopilotContext context) {
        return recommendationEngine.generate(context);
    }

    public String explain(CopilotRecommendation recommendation) {
        return explanationEngine.explain(recommendation);
    }

    public Map<String, Object> dashboard(CopilotContext context) {
        return dashboardSummaryService.buildSummary(context);
    }

    public CareerHealthScoreService.ScoreResult healthScore(CopilotContext context) {
        return careerHealthScoreService.calculate(context);
    }
}
