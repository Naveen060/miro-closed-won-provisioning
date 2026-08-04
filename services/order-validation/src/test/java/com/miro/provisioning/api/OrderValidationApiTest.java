package com.miro.provisioning.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
/**
 * End-to-end HTTP contract tests that load the real Spring filters, controller,
 * validation advice, and idempotency services without opening a network port.
 */
class OrderValidationApiTest {

    private static final String VALID_REQUEST = """
            {
              "accountId": "acct-123",
              "totalAmount": 12500.50,
              "currency": "usd",
              "countryCode": "us",
              "opportunityId": "opp-456"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsRequestsWithoutTheApiKey() throws Exception {
        // Authentication runs before controller logic, even for an otherwise
        // valid payload and idempotency key.
        mockMvc.perform(post("/api/v1/orders/validate")
                        .header("Idempotency-Key", "unauthorized-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void rejectsMissingRequiredFields() throws Exception {
        // Bean Validation should aggregate both missing required fields into the
        // stable API error envelope rather than exposing framework exceptions.
        mockMvc.perform(post("/api/v1/orders/validate")
                        .header("X-API-Key", "local-demo-key")
                        .header("Idempotency-Key", "invalid-fields-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.length()").value(2));
    }

    @Test
    void cachesAndReplaysTheExactSuccessResponse() throws Exception {
        MvcResult first = performValidRequest("replay-test", VALID_REQUEST)
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(header().string("X-Correlation-Id", "corr-replay-test"))
                .andExpect(jsonPath("$.validationStatus").value("VALID"))
                .andExpect(jsonPath("$.taxRoute").value("US_STATE_SALES_TAX"))
                .andReturn();

        // Repeat the byte-identical request and require both replay metadata and
        // an unchanged body, including the original processedAt timestamp.
        MvcResult replay = performValidRequest("replay-test", VALID_REQUEST)
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andReturn();

        assertThat(replay.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
    }

    @Test
    void rejectsReusingAKeyForADifferentPayload() throws Exception {
        performValidRequest("conflict-test", VALID_REQUEST)
                .andExpect(status().isOk());

        // Change only the amount to prove fingerprinting covers request content,
        // not just the route or caller-provided key.
        performValidRequest("conflict-test", VALID_REQUEST.replace("12500.50", "15000.00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    private org.springframework.test.web.servlet.ResultActions performValidRequest(
            String idempotencyKey,
            String body
    ) throws Exception {
        // Centralize mandatory integration headers so each test varies only the
        // idempotency key and payload relevant to its scenario.
        return mockMvc.perform(post("/api/v1/orders/validate")
                .header("X-API-Key", "local-demo-key")
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Correlation-Id", "corr-replay-test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
