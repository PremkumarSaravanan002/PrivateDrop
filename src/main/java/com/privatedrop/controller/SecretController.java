package com.privatedrop.controller;

import com.privatedrop.dto.CreateSecretRequest;
import com.privatedrop.dto.CreateSecretResult;
import com.privatedrop.dto.RetrieveSecretResult;
import com.privatedrop.service.SecretService;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/secrets")
public class SecretController {

    private final SecretService secretService;

    public SecretController(SecretService secretService) {
        this.secretService = secretService;
    }


    /*
    * This endpoint is gets the Payload and create a secret entry
    * Returns secretId and Encoded Access Token
    */
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


    /*
    * This endpoint helps to retrieve the stored Secret
    * This accepts secretId and AccessToken (Request Header)
    *
    * Returns encryptedPayload + Nonce
    * */
    @PostMapping("/{secretId}/reveal")
    public RetrieveSecretResult retrieveSecret(
        @PathVariable UUID secretId,
        @RequestHeader("X-Access-Token") String accessToken
    ){
        byte[] suppliedToken = Base64.getUrlDecoder().decode(accessToken);

        return secretService.retrieveSecret(
                secretId,
                suppliedToken
        );
    }
}
