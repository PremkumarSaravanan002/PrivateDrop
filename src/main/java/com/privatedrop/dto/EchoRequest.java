package com.privatedrop.dto;

import jakarta.validation.constraints.NotBlank;

public record EchoRequest(
        @NotBlank(message="Message cannot be empty")
        String message
) {
}
