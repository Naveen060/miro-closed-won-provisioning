package com.miro.provisioning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miro.provisioning.domain.OrderValidationRequest;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
/**
 * Produces a stable digest of the request payload used to bind an idempotency
 * key to one logical operation. Reusing the key is safe only when this digest
 * matches the first request observed for that key.
 */
public class RequestFingerprint {

    private final ObjectMapper objectMapper;

    public RequestFingerprint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String forRequest(OrderValidationRequest request) {
        try {
            // ObjectMapper serializes the record in a deterministic property order
            // for this fixed DTO, giving equivalent requests the same byte input.
            byte[] canonicalRequest = objectMapper.writeValueAsBytes(request);
            // Store only the SHA-256 digest in the idempotency table. This avoids
            // retaining business payloads while still detecting changed requests.
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonicalRequest)
            );
        } catch (JsonProcessingException | NoSuchAlgorithmException error) {
            // SHA-256 is required by the JVM; either failure means the service
            // cannot safely enforce its idempotency contract and must fail closed.
            throw new IllegalStateException("Could not fingerprint validation request", error);
        }
    }
}
