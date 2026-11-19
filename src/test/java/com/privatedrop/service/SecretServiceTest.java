package com.privatedrop.service;

import com.privatedrop.dto.RetrieveSecretResult;
import com.privatedrop.entity.Secret;
import com.privatedrop.exception.SecretUnavailableException;
import com.privatedrop.repository.SecretRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SecretServiceTest {

    @Mock
    private SecretRepository secretRepository;

    private SecretService secretService;

    @BeforeEach
    void setUp(){
        secretService = new SecretService(secretRepository);
    }

    @Test
    void retrieveSecret_whenTokenIsValid_returnsEncryptedPayloadAndNonce(){

        UUID secretId = UUID.randomUUID();

        byte[] suppliedToken = new byte[32];
        byte[] encryptedPayload = new byte[]{10,30,20,40};
        byte[] nonce = new byte[12];

        Secret secret = new Secret(
                encryptedPayload,
                nonce,
                new byte[32],
                Instant.now().plusSeconds(300),
                1
        );

        when(secretRepository.claimView(eq(secretId), any(byte[].class)))
                .thenReturn(1);

        when(secretRepository.findById(secretId))
                .thenReturn(Optional.of(secret));

        //Act
        RetrieveSecretResult result = secretService.retrieveSecret(
                secretId,
                suppliedToken
        );

        //Assert
        assertArrayEquals(encryptedPayload, result.encryptedPayload());
        assertArrayEquals(nonce, result.nonce());
    }

    @Test
    void retrieveSecret_whenViewCannotBeClaimed_throwsSecretUnavailableException(){

        //Arrange
        UUID secretId = UUID.randomUUID();
        byte[] suppliedToken = new byte[32];

        when(secretRepository.claimView(eq(secretId), any(byte[].class)))
        .thenReturn(0);

        //Act & Assert
        assertThrows(
                SecretUnavailableException.class,
                () -> secretService.retrieveSecret(secretId, suppliedToken)
        );

        //Verify
        verify(secretRepository, never()).findById(secretId);
    }
}
