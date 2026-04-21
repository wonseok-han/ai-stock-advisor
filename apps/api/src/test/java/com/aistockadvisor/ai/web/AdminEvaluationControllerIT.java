package com.aistockadvisor.ai.web;

import com.aistockadvisor.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 참조: docs/02-design/features/signal-accuracy.design.md §4.2, §8.2
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.admin.username=admin",
        "app.admin.password=test-secret"
})
class AdminEvaluationControllerIT {

    @Autowired MockMvc mvc;

    @Test
    void rejects_without_basic_auth() throws Exception {
        mvc.perform(post("/api/admin/ai/backfill-evaluation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"window\":7}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejects_with_wrong_password() throws Exception {
        mvc.perform(post("/api/admin/ai/backfill-evaluation")
                        .header("Authorization", basic("admin", "wrong"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"window\":7}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accepts_valid_request_and_returns_202() throws Exception {
        mvc.perform(post("/api/admin/ai/backfill-evaluation")
                        .header("Authorization", basic("admin", "test-secret"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"window\":7}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.scheduled").value(true))
                .andExpect(jsonPath("$.batchSize").isNumber());
    }

    @Test
    void rejects_unsupported_window() throws Exception {
        mvc.perform(post("/api/admin/ai/backfill-evaluation")
                        .header("Authorization", basic("admin", "test-secret"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"window\":14}"))
                .andExpect(status().isBadRequest());
    }

    private static String basic(String user, String pw) {
        return "Basic " + java.util.Base64.getEncoder()
                .encodeToString((user + ":" + pw).getBytes());
    }
}
