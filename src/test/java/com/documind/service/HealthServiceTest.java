package com.documind.service;

import com.documind.dto.HealthResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HealthServiceTest {

    private final HealthService healthService = new HealthService();

    @Test
    void getHealthShouldReturnUpAndApplicationName() {
        HealthResponse response = healthService.getHealth();

        assertNotNull(response);
        assertEquals("UP", response.status());
        assertEquals("DocuMind", response.application());
    }
}
