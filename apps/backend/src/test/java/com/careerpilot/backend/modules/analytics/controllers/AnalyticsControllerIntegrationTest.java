package com.careerpilot.backend.modules.analytics.controllers;

import com.careerpilot.backend.modules.analytics.domain.*;
import com.careerpilot.backend.modules.analytics.repositories.*;
import com.careerpilot.backend.modules.analytics.services.CareerAnalyticsService;
import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AnalyticsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private CareerAnalyticsService analyticsService;

    @MockBean
    private CareerGoalRepository careerGoalRepository;

    @MockBean
    private CareerGoalProgressRepository goalProgressRepository;

    @MockBean
    private LearningPathRepository learningPathRepository;

    @MockBean
    private LearningProgressRepository learningProgressRepository;

    private UUID testUserId;
    private User testUser;

    @BeforeEach
    public void setUp() {
        testUserId = UUID.randomUUID();
        testUser = User.builder()
                .id(testUserId)
                .email("candidate@example.com")
                .firstName("Alice")
                .lastName("Builder")
                .build();

        when(userRepository.findByEmail("candidate@example.com")).thenReturn(Optional.of(testUser));
    }

    @Test
    @WithMockUser(username = "candidate@example.com", roles = "USER")
    public void testGetOverview_Success() throws Exception {
        CareerAnalytics mockAnalytics = CareerAnalytics.builder()
                .candidateId(testUserId)
                .analysisPeriod("MONTHLY")
                .careerGrowthScore(75.5)
                .averageMatchScore(82.0)
                .averageInterviewReadiness(0.78)
                .resumeScore(85.0)
                .build();

        when(analyticsService.calculateAndPersistAnalytics(eq(testUserId), eq("MONTHLY")))
                .thenReturn(mockAnalytics);

        mockMvc.perform(get("/api/v1/analytics/overview")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.careerGrowthScore").value(75.5))
                .andExpect(jsonPath("$.averageMatchScore").value(82.0));
    }

    @Test
    public void testGetOverview_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/analytics/overview")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden()); // Spring Security blocks unauthenticated
    }

    @Test
    @WithMockUser(username = "candidate@example.com", roles = "USER")
    public void testCreateGoal_Success() throws Exception {
        LearningController.GoalRequest request = new LearningController.GoalRequest(
                "Senior Backend Engineer", "FinTech", 3000000.0, "Bangalore", 12
        );

        CareerGoal mockGoal = CareerGoal.builder()
                .id(UUID.randomUUID())
                .candidateId(testUserId)
                .targetRole("Senior Backend Engineer")
                .targetIndustry("FinTech")
                .build();

        when(analyticsService.createGoal(eq(testUserId), anyString(), anyString(), anyDouble(), anyString(), anyInt()))
                .thenReturn(mockGoal);

        mockMvc.perform(post("/api/v1/learning/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetRole").value("Senior Backend Engineer"))
                .andExpect(jsonPath("$.targetIndustry").value("FinTech"));
    }

    @Test
    @WithMockUser(username = "candidate@example.com", roles = "USER")
    public void testGetGoals_Success() throws Exception {
        UUID goalId = UUID.randomUUID();
        CareerGoal mockGoal = CareerGoal.builder()
                .id(goalId)
                .candidateId(testUserId)
                .targetRole("Senior Backend Engineer")
                .build();

        CareerGoalProgress mockProgress = CareerGoalProgress.builder()
                .goalId(goalId)
                .overallProgress(25.0)
                .isCompleted(false)
                .build();

        when(careerGoalRepository.findByCandidateId(testUserId)).thenReturn(List.of(mockGoal));
        when(goalProgressRepository.findByGoalId(goalId)).thenReturn(Optional.of(mockProgress));

        mockMvc.perform(get("/api/v1/learning/goals")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].goal.targetRole").value("Senior Backend Engineer"))
                .andExpect(jsonPath("$[0].progress.overallProgress").value(25.0));
    }
}
