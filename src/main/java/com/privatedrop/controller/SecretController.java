package com.privatedrop.controller;

import com.privatedrop.dto.CreateSecretRequest;
import com.privatedrop.dto.CreateSecretResult;
import com.privatedrop.service.SecretService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;


@RestController
@RequestMapping("/api/v1/secrets")
public class SecretController {

    private final SecretService secretService;

    public SecretController(SecretService secretService) {
        this.secretService = secretService;
    }

    @PostMapping
    public CreateSecretResult createSecret(
            @RequestBody CreateSecretRequest request
    ){
        byte[] encryptedPayload = Base64.getDecoder().decode(request.encryptedPayload());
        byte[] nonce = Base64.getDecoder().decode(request.nonce());
        return secretService.createSecret(
                encryptedPayload,
                nonce,
                request.expiresAt(),
                request.maxViews()
        );
    }
}
