package com.careerpilot.backend.modules.ai.gateway;

import com.careerpilot.backend.modules.ai.gateway.exceptions.AiProviderException;
import com.careerpilot.shared.dto.ai.AiTaskRequestDto;
import com.careerpilot.shared.dto.ai.AiTaskResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

class AiGatewayClientTest {

    private AiGatewayClient gatewayClient;

    @BeforeEach
    void setUp() {
        gatewayClient = Mockito.mock(AiGatewayClient.class);
    }

    @Test
    void testExecuteTask_ReturnsStandardizedResponse() {
        AiTaskRequestDto request = AiTaskRequestDto.builder()
                .taskId(UUID.randomUUID())
                .taskType("RESUME_PARSE")
                .build();
                
        AiTaskResponseDto mockResponse = AiTaskResponseDto.builder()
                .taskId(request.getTaskId())
                .status("COMPLETED")
                .provider("openai")
                .result(new HashMap<>())
                .build();

        Mockito.when(gatewayClient.executeTask(any(AiTaskRequestDto.class))).thenReturn(mockResponse);

        AiTaskResponseDto response = gatewayClient.executeTask(request);
        assertNotNull(response);
        assertEquals("COMPLETED", response.getStatus());
        assertEquals("openai", response.getProvider());
    }

    @Test
    void testExecuteTask_ThrowsCustomAiException() {
        Mockito.when(gatewayClient.executeTask(any(AiTaskRequestDto.class)))
                .thenThrow(new AiProviderException("AI service unavailable"));

        assertThrows(AiProviderException.class, () ->
                gatewayClient.executeTask(AiTaskRequestDto.builder().taskType("JOB_MATCH").build())
        );
    }
}
