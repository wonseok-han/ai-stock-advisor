package com.aistockadvisor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Actuator 엔드포인트 노출 범위 검증 (Design §5.2 + Acceptance §9).
 *
 * <ul>
 *   <li>A-1: /actuator/prometheus → HTTP 200 + text/plain + llm_ prefix 시계열 ≥ 8 라인</li>
 *   <li>A-2: /actuator/env → HTTP 404 (exposure.include 에 미포함)</li>
 * </ul>
 *
 * <p>참조: docs/02-design/features/phase2.1-metrics-fe-refactor.design.md §5.2, §9
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ActuatorExposureTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("A-1 /actuator/prometheus → HTTP 200 + text/plain + llm_call_count_total 시계열 포함")
    void prometheusEndpointExposedWithLlmMetrics() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(content().string(containsString("llm_call_count_total")));
    }

    @Test
    @DisplayName("A-2 /actuator/env → HTTP 404 (exposure.include 미포함)")
    void envEndpointNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isNotFound());
    }
}
