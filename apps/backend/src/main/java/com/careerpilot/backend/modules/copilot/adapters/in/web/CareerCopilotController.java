package com.careerpilot.backend.modules.copilot.adapters.in.web;

import com.careerpilot.backend.config.CurrentUser;
import com.careerpilot.backend.modules.copilot.services.CareerHealthService;
import com.careerpilot.backend.modules.copilot.services.CopilotService;
import com.careerpilot.backend.modules.copilot.services.CopilotTools;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/** Career Copilot. Identity always comes from the authenticated principal; memory is per user. */
@RestController
@RequestMapping("/api/v1/copilot")
@Tag(name = "Career Copilot", description = "AI agent that answers from your real CareerPilot data using read-only tools")
@RequiredArgsConstructor
public class CareerCopilotController {

    private final CopilotService copilotService;
    private final CareerHealthService careerHealthService;
    private final CurrentUser currentUser;

    @PostMapping("/chat")
    @Operation(summary = "Ask the Copilot a question")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> body, Principal principal) {
        String message = body.getOrDefault("message", body.get("query"));
        return ResponseEntity.ok(copilotService.chat(currentUser.requireId(principal), message));
    }

    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> history(@RequestParam(defaultValue = "50") int limit, Principal principal) {
        return ResponseEntity.ok(copilotService.history(currentUser.requireId(principal), limit));
    }

    @PostMapping("/clear-session")
    public ResponseEntity<Map<String, Object>> clear(Principal principal) {
        long deleted = copilotService.clear(currentUser.requireId(principal));
        return ResponseEntity.ok(Map.of("deletedMessages", deleted));
    }

    @GetMapping("/tools")
    @Operation(summary = "The read-only tools the Copilot can use")
    public ResponseEntity<List<CopilotTools.ToolSpec>> tools() {
        return ResponseEntity.ok(copilotService.catalog());
    }

    @GetMapping("/health-score")
    @Operation(summary = "Career health computed from your real data (unavailable components are reported, not guessed)")
    public ResponseEntity<Map<String, Object>> health(Principal principal) {
        return ResponseEntity.ok(careerHealthService.health(currentUser.requireId(principal)));
    }
}
