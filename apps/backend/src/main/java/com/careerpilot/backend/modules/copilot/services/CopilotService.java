package com.careerpilot.backend.modules.copilot.services;

import com.careerpilot.backend.modules.ai.gateway.AiGatewayClient;
import com.careerpilot.backend.modules.ai.gateway.exceptions.AiServiceException;
import com.careerpilot.backend.modules.copilot.domain.CopilotMessage;
import com.careerpilot.backend.modules.copilot.domain.CopilotToolAudit;
import com.careerpilot.backend.modules.copilot.repositories.CopilotMessageRepository;
import com.careerpilot.backend.modules.copilot.repositories.CopilotToolAuditRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/**
 * Career Copilot agent loop:
 * <ol>
 *   <li>Plan: the model picks read-only tools from an allow-list (keyword routing if planning fails).</li>
 *   <li>Act: the server runs each tool for the authenticated user; every call is audited.</li>
 *   <li>Answer: the model answers only from the tool results; citations and suggested actions are
 *       filtered to what was actually retrieved / allowed.</li>
 * </ol>
 * The Copilot never performs writes. Suggested actions are links the user must confirm in the UI.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CopilotService {

    static final int MAX_TOOLS = 4;
    static final int HISTORY_TURNS = 6;
    static final int MAX_MESSAGE_CHARS = 4000;

    /** Actions the UI can render as buttons; each maps to an existing, authorized endpoint/page. */
    static final List<String> ALLOWED_ACTIONS = List.of(
            "OPEN_JOB", "ANALYZE_JOB", "OPEN_APPLICATION", "START_INTERVIEW_PRACTICE", "OPTIMIZE_RESUME",
            "DRAFT_FOLLOW_UP", "GENERATE_COVER_LETTER", "VIEW_LEARNING_PLAN", "UPLOAD_RESUME");

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final CopilotTools tools;
    private final AiGatewayClient gatewayClient;
    private final CopilotMessageRepository messageRepository;
    private final CopilotToolAuditRepository auditRepository;

    public Map<String, Object> chat(UUID userId, String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message must not be empty");
        }
        if (message.length() > MAX_MESSAGE_CHARS) {
            throw new IllegalArgumentException("Message is too long (max " + MAX_MESSAGE_CHARS + " characters)");
        }
        List<Map<String, String>> history = recentHistory(userId);
        save(userId, CopilotMessage.ROLE_USER, message, null, null, null, null);

        // 1. Plan
        List<Map<String, Object>> catalog = tools.catalog().stream().map(spec -> Map.<String, Object>of(
                "name", spec.name(), "description", spec.description(), "arguments", spec.arguments())).toList();
        List<PlannedCall> plan = new ArrayList<>();
        String selectedBy = "MODEL";
        try {
            Map<String, Object> planned = gatewayClient.run("COPILOT_PLAN",
                    Map.of("question", message, "toolCatalog", catalog, "history", history));
            for (Object o : (List<?>) planned.getOrDefault("tools", List.of())) {
                if (o instanceof Map<?, ?> call && call.get("name") instanceof String name && tools.exists(name)) {
                    Map<String, String> args = new HashMap<>();
                    if (call.get("arguments") instanceof Map<?, ?> a) {
                        a.forEach((k, v) -> args.put(String.valueOf(k), String.valueOf(v)));
                    }
                    plan.add(new PlannedCall(name, args));
                }
            }
        } catch (AiServiceException e) {
            log.warn("Copilot planning failed ({}); using keyword routing", e.getCode());
        }
        if (plan.isEmpty()) {
            plan = KeywordRouter.route(message);
            selectedBy = "FALLBACK";
        }

        // 2. Act (read-only, audited)
        List<Map<String, Object>> toolResults = new ArrayList<>();
        for (PlannedCall call : plan.stream().limit(MAX_TOOLS).toList()) {
            long start = System.currentTimeMillis();
            try {
                Object data = tools.execute(userId, call.name(), call.arguments());
                toolResults.add(Map.of("tool", call.name(), "data", data));
                audit(userId, call, selectedBy, true, null, System.currentTimeMillis() - start);
            } catch (RuntimeException e) {
                toolResults.add(Map.of("tool", call.name(), "error", safeMessage(e)));
                audit(userId, call, selectedBy, false, safeMessage(e), System.currentTimeMillis() - start);
            }
        }

        // 3. Answer from the retrieved data only
        Map<String, Object> answerPayload = new HashMap<>();
        answerPayload.put("question", message);
        answerPayload.put("toolResults", toJsonSafe(toolResults));
        answerPayload.put("allowedActions", ALLOWED_ACTIONS);
        answerPayload.put("history", history);
        Map<String, Object> answer = gatewayClient.run("COPILOT_ANSWER", answerPayload);

        String text = Objects.toString(answer.get("answer"), "");
        save(userId, CopilotMessage.ROLE_ASSISTANT, text, plan.stream().map(PlannedCall::name).toList(),
                answer.get("citations"), answer.get("suggestedActions"), null);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("answer", text);
        response.put("citations", answer.getOrDefault("citations", List.of()));
        response.put("suggestedActions", answer.getOrDefault("suggestedActions", List.of()));
        response.put("dataGaps", answer.getOrDefault("dataGaps", List.of()));
        response.put("verification", answer.get("verification"));
        response.put("toolsUsed", plan.stream().map(p -> Map.of("name", p.name(), "arguments", p.arguments())).toList());
        response.put("toolSelection", selectedBy);
        return response;
    }

    public List<Map<String, Object>> history(UUID userId, int limit) {
        List<CopilotMessage> messages = new ArrayList<>(
                messageRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, Math.min(limit, 200))));
        Collections.reverse(messages);
        return messages.stream().map(m -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.getId());
            row.put("role", m.getRole());
            row.put("content", m.getContent());
            row.put("toolsUsed", fromJson(m.getToolsJson()));
            row.put("citations", fromJson(m.getCitationsJson()));
            row.put("suggestedActions", fromJson(m.getActionsJson()));
            row.put("createdAt", m.getCreatedAt());
            return row;
        }).toList();
    }

    public long clear(UUID userId) {
        return messageRepository.deleteByUserId(userId);
    }

    public List<CopilotTools.ToolSpec> catalog() {
        return tools.catalog();
    }

    // ------------------------------------------------------------------ helpers

    record PlannedCall(String name, Map<String, String> arguments) {
    }

    private List<Map<String, String>> recentHistory(UUID userId) {
        List<CopilotMessage> recent = new ArrayList<>(
                messageRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, HISTORY_TURNS)));
        Collections.reverse(recent);
        return recent.stream().map(m -> Map.of("role", m.getRole(),
                "content", m.getContent().length() > 600 ? m.getContent().substring(0, 600) : m.getContent())).toList();
    }

    private void save(UUID userId, String role, String content, Object tools, Object citations, Object actions, String model) {
        messageRepository.save(CopilotMessage.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .role(role)
                .content(content == null || content.isBlank() ? "(no answer)" : content)
                .toolsJson(toJson(tools))
                .citationsJson(toJson(citations))
                .actionsJson(toJson(actions))
                .aiModel(model)
                .createdAt(Instant.now())
                .build());
    }

    private void audit(UUID userId, PlannedCall call, String selectedBy, boolean success, String error, long durationMs) {
        auditRepository.save(CopilotToolAudit.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .correlationId(MDC.get("correlationId"))
                .toolName(call.name())
                .argumentsJson(toJson(call.arguments()))
                .selectedBy(selectedBy)
                .success(success)
                .errorMessage(error != null && error.length() > 500 ? error.substring(0, 500) : error)
                .durationMs(durationMs)
                .createdAt(Instant.now())
                .build());
    }

    private static String safeMessage(RuntimeException e) {
        return e instanceof AiServiceException ai ? "AI unavailable (" + ai.getCode() + ")"
                : e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    /** Convert tool results to plain JSON-compatible structures (dates, records, DTOs). */
    private static Object toJsonSafe(Object value) {
        try {
            return MAPPER.readValue(MAPPER.writeValueAsString(value), Object.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String toJson(Object o) {
        if (o == null) return null;
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object fromJson(String json) {
        if (json == null) return List.of();
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Deterministic tool routing used only when the model's plan is unavailable. */
    static final class KeywordRouter {
        private static final Map<String, String> RULES = new LinkedHashMap<>();

        static {
            RULES.put("follow", "get_follow_up_recommendations");
            RULES.put("recruiter|email|respond|replied|reply|heard back", "get_recent_communications");
            RULES.put("application|pipeline|applied|status", "get_applications");
            RULES.put("match|apply to|which jobs|best job|good fit|recommend.*job", "get_top_job_matches");
            RULES.put("skill|missing|gap|learn", "get_skill_gaps");
            RULES.put("interview|prepare|practice|readiness", "get_interview_readiness");
            RULES.put("resume|cv|ats", "get_resume_insights");
            RULES.put("health|progress|how am i doing", "get_career_health");
            RULES.put("course|learning", "get_learning_progress");
        }

        static List<PlannedCall> route(String message) {
            String m = message.toLowerCase(Locale.ROOT);
            List<PlannedCall> calls = new ArrayList<>();
            for (Map.Entry<String, String> rule : RULES.entrySet()) {
                if (java.util.regex.Pattern.compile(rule.getKey()).matcher(m).find()
                        && calls.stream().noneMatch(c -> c.name().equals(rule.getValue()))) {
                    calls.add(new PlannedCall(rule.getValue(), Map.of()));
                }
            }
            if (calls.isEmpty()) {
                calls.add(new PlannedCall("get_profile_summary", Map.of()));
            }
            return calls;
        }
    }
}
