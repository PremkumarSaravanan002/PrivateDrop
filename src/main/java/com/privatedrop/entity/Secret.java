package com.privatedrop.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;


@Entity
@Table(
        name = "secrets",
        check = {
                @CheckConstraint(
                        name = "chk_secrets_view_counts",
                        constraint = "max_views >=1 AND view_count >= 0 AND view_count <= max_views"
                )
        }
)
public class Secret {

    protected Secret(){
    }

    public Secret(
            byte[] encryptedPayload,
            byte[] nonce,
            byte[] tokenHash,
            Instant expiresAt,
            int maxViews
    ){
        this.encryptedPayload = Arrays.copyOf(encryptedPayload, encryptedPayload.length);
        this.nonce = Arrays.copyOf(nonce, nonce.length);
        this.tokenHash = Arrays.copyOf(tokenHash, tokenHash.length);
        this.expiresAt = expiresAt;
        this.maxViews = maxViews;
    }


    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "encrypted_payload", columnDefinition = "BYTEA", nullable = false)
    private byte[] encryptedPayload;

    @Column(name = "nonce",columnDefinition = "BYTEA", nullable = false)
    private byte[] nonce;

    @Column(name = "token_hash", columnDefinition = "BYTEA", nullable = false)
    private byte[] tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "max_views", nullable = false)
    private int maxViews = 1;

    @Column(name = "view_count", nullable = false)
    private int viewCount = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;


    @PrePersist
    protected void onCreate(){
        if(createdAt == null){
            createdAt = Instant.now();
        }
    }


    public UUID getId() {
        return id;
    }

    public byte[] getEncryptedPayload() {
//        return encryptedPayload; // we dont want to return actual byte array as byte arrays are modifiable so, return copy of array
        return Arrays.copyOf(encryptedPayload, encryptedPayload.length);
    }

    public byte[] getNonce() {
//        return nonce;
        return  Arrays.copyOf(nonce, nonce.length);
    }

    public byte[] getTokenHash() {
//        return tokenHash;
        return  Arrays.copyOf(tokenHash, tokenHash.length);
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getMaxViews() {
        return maxViews;
    }

    public int getViewCount() {
        return viewCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }
}
