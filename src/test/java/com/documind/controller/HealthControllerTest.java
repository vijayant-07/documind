package com.documind.controller;

import com.documind.dto.HealthResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthControllerTest {

    @Test
    void healthShouldReturnUpStatusAndApplicationName() {
        // Arrange
        HealthController controller = new HealthController();

        // Act
        HealthResponse response = controller.health();

        // Assert
        assertEquals("UP", response.status());
        assertEquals("DocuMind", response.application());
    }
}
