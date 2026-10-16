package com.documind.controller;

import com.documind.dto.HealthResponse;
import com.documind.service.HealthService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    @Test
    void healthShouldReturnServiceResponse() {
        // Arrange
        HealthService healthService = mock(HealthService.class);

        when(healthService.getHealth())
                .thenReturn(new HealthResponse("UP", "DocuMind"));

        HealthController controller = new HealthController(healthService);

        // Act
        HealthResponse response = controller.health();

        // Assert
        assertEquals("UP", response.status());
        assertEquals("DocuMind", response.application());
    }
}