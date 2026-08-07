package com.careerpilot.backend.modules.copilot.adapters.in.web;

import com.careerpilot.backend.modules.copilot.domain.CopilotContext;
import com.careerpilot.backend.modules.copilot.domain.CopilotPlan;
import com.careerpilot.backend.modules.copilot.domain.CopilotRecommendation;
import com.careerpilot.backend.modules.copilot.services.CareerCopilotEngine;
import com.careerpilot.backend.modules.copilot.services.ConversationMemory;
import com.careerpilot.backend.modules.copilot.services.CareerHealthScoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/copilot")
public class CareerCopilotController {
    private final CareerCopilotEngine engine;
    private final ConversationMemory memory;

    public CareerCopilotController(CareerCopilotEngine engine, ConversationMemory memory) {
        this.engine = engine;
        this.memory = memory;
    }

    @PostMapping("/chat")
    public ResponseEntity<CopilotContext> chat(@RequestBody ChatRequest request) {
        return ResponseEntity.ok(engine.process(request.message(), request.userId()));
    }

    @PostMapping("/plan")
    public ResponseEntity<CopilotPlan> plan(@RequestBody PlanRequest request) {
        CopilotContext context = engine.process(request.message(), request.userId());
        return ResponseEntity.ok(engine.createPlan(context));
    }

    @PostMapping("/recommendations")
    public ResponseEntity<List<CopilotRecommendation>> recommendations(@RequestBody ChatRequest request) {
        CopilotContext context = engine.process(request.message(), request.userId());
        return ResponseEntity.ok(engine.recommend(context));
    }

    @PostMapping("/explain-match")
    public ResponseEntity<String> explainMatch(@RequestBody ExplainRequest request) {
        CopilotRecommendation recommendation = new CopilotRecommendation();
        recommendation.setTitle("Learn AWS");
        recommendation.setReason("72% of your top matching backend jobs require AWS.");
        recommendation.setEvidence(List.of("18 matching jobs require AWS", "Current resume does not include AWS"));
        return ResponseEntity.ok(engine.explain(recommendation));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard(@RequestParam(required = false) String userId) {
        CopilotContext context = engine.process("dashboard summary", userId == null ? "demo" : userId);
        return ResponseEntity.ok(engine.dashboard(context));
    }

    @GetMapping("/health-score")
    public ResponseEntity<CareerHealthScoreService.ScoreResult> healthScore(@RequestParam(required = false) String userId) {
        CopilotContext context = engine.process("health score", userId == null ? "demo" : userId);
        return ResponseEntity.ok(engine.healthScore(context));
    }

    @GetMapping("/history")
    public ResponseEntity<List<?>> history() {
        return ResponseEntity.ok(memory.getHistory());
    }

    @PostMapping("/clear-session")
    public ResponseEntity<Void> clearSession() {
        memory.clear();
        return ResponseEntity.ok().build();
    }

    public record ChatRequest(String message, String userId) {}
    public record PlanRequest(String message, String userId) {}
    public record ExplainRequest(String message) {}
}
