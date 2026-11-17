package com.privatedrop.dto;

public record RetrieveSecretResult(
        byte[] encryptedPayload,
        byte[] nonce
) {
}
