
package com.privatedrop.service;

import com.privatedrop.dto.CreateSecretResult;
import com.privatedrop.dto.RetrieveSecretResult;
import com.privatedrop.entity.Secret;
import com.privatedrop.exception.SecretUnavailableException;
import com.privatedrop.repository.SecretRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class SecretService {

    private final SecretRepository secretRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    public SecretService(SecretRepository secretRepository) {
        this.secretRepository = secretRepository;
    }

    /*
     * We are generating the access token, which decides whether the request
     * is authorized to access the message.
     *
     * Initially, we will give the access token to the client. Then we will
     * hash it and store the hash in our database.
     */
    private byte[] generateAccessToken() {
        byte[] token = new byte[32];

        // Generates 32 bytes (256 bits) of cryptographically secure random data.
        secureRandom.nextBytes(token);

        return token;
    }

    /*
     * Here we will generate the hash of the access token to store in our database.
     *
     * We use SHA-256 to generate a fixed-length hash of the raw token.
     * The resulting hash is 32 bytes (256 bits).
     */
    private byte[] hashAccessToken(byte[] token) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");

            // Calculates the SHA-256 hash of the provided token.
            return messageDigest.digest(token);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable", e
            );
        }
    }

    /*
     * We generate the access token, then generate its hash to store in the
     * database. Finally, we encode the raw token using Base64 URL encoding
     * so that it can be represented as text and sent to the client.
     *
     * Base64 is encoding, NOT encryption.
     */
    private String encodeAccessToken(byte[] token) {

        return Base64.getUrlEncoder()
                .withoutPadding() // Omits padding characters (=) from the encoded output.
                .encodeToString(token);
    }


    private void validateCreateSecretRequest(
            byte[] encryptedPayload,
            byte[] nonce,
            Instant expiresAt,
            int maxViews
    ){
        /*
        *AES-GCM appends a 128-bit authentication tag to the ciphertext.
        * 128 bits = 16 bytes.
        * Even an empty plaintext produces a 16-byte authentication tag.
        * Therefore, a valid AES-GCM encrypted payload must contain at least 16 bytes
        *
        */
        if(encryptedPayload == null || encryptedPayload.length < 16){
            throw new IllegalArgumentException("Encrypted payload must contain at least 16 bytes");
        }

        //we're choosing a 96-bit (12-byte) nonce for AES-GCM.
        if(nonce == null || nonce.length != 12){
            throw new IllegalArgumentException("Nonce must be exactly 12 bytes");
        }

        if( expiresAt == null || !expiresAt.isAfter(Instant.now()) ){
            throw new IllegalArgumentException("Expiry time must be in the future");
        }

        if( maxViews < 1 ){
            throw new  IllegalArgumentException("Max views must be at least 1");
        }
    }


    /*
     * Create new secret by generating an access token,
     * hashing it for database storage and saving encrypted
     * payload along with its access rules
     *
     * Returns Secret ID and encoded access token to client
     */
    public CreateSecretResult createSecret(
            byte[] encryptedPayload,
            byte[] nonce,
            Instant expiresAt,
            int maxViews
    ) {
        validateCreateSecretRequest(
                encryptedPayload,
                nonce,
                expiresAt,
                maxViews
        );

        byte[] rawAccessToken = generateAccessToken();

        byte[] tokenHash = hashAccessToken(rawAccessToken);

        String encodedAccessToken = encodeAccessToken(rawAccessToken);

        Secret secret = new Secret(
                encryptedPayload,
                nonce,
                tokenHash,
                expiresAt,
                maxViews
        );

        Secret savedSecret = secretRepository.save(secret);

        return new CreateSecretResult(
                savedSecret.getId(),
                encodedAccessToken
        );
    }


    /*
    * Adding Transactional as it involves two database operations
    *   Atomically claim one view using claimView() in secretRepository
    *   Fetch the encrypted payload and nonce
    */
    @Transactional
    public RetrieveSecretResult retrieveSecret(
            UUID secretId,
            byte[] suppliedToken
    ){
        if(secretId == null){
            throw new SecretUnavailableException("Secret ID is required");
        }

        if(suppliedToken == null || suppliedToken.length != 32){
            throw new IllegalArgumentException("Invalid Access Token");
        }

        byte[] tokenHash = hashAccessToken(suppliedToken);

        //claim one view using claimView() in secretRepository
        int claimedViews = secretRepository.claimView(secretId, tokenHash);

        if(claimedViews != 1){
            throw new SecretUnavailableException("Secret is unavailable");
        }


        // Fetch the encrypted payload and nonce
        Secret secret = secretRepository.findById(secretId)
                .orElseThrow(() -> new IllegalStateException(
                        "Secret disappeared after claiming a view"
                ));

        return new RetrieveSecretResult(
                secret.getEncryptedPayload(),
                secret.getNonce()
        );


    }
}
