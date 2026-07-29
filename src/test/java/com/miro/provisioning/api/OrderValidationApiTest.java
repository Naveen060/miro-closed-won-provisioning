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
        mockMvc.perform(post("/api/v1/orders/validate")
                        .header("Idempotency-Key", "unauthorized-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void rejectsMissingRequiredFields() throws Exception {
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

        performValidRequest("conflict-test", VALID_REQUEST.replace("12500.50", "15000.00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    private org.springframework.test.web.servlet.ResultActions performValidRequest(
            String idempotencyKey,
            String body
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/orders/validate")
                .header("X-API-Key", "local-demo-key")
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Correlation-Id", "corr-replay-test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}

