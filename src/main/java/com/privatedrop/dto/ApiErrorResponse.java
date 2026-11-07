package com.privatedrop.dto;

public record ApiErrorResponse(
        int status,
        String error,
        String message
) {
}
