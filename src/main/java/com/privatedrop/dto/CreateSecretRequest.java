package com.privatedrop.dto;

import java.time.Instant;

public record CreateSecretRequest(
        String encryptedPayload,
        String nonce,
        Instant expiresAt,
        int maxViews
) {
}
