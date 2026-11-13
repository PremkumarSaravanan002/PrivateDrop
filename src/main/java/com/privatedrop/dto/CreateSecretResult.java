package com.privatedrop.dto;

import java.util.UUID;

public record CreateSecretResult(
        UUID secretId,
        String accessToken
) {
}
