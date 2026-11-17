package com.privatedrop.repository;

import com.privatedrop.entity.Secret;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface SecretRepository  extends JpaRepository<Secret, UUID> {

    @Modifying
    @Query("""
            UPDATE Secret s
            SET s.viewCount = s.viewCount + 1
            WHERE s.id = :secretId
                        AND s.tokenHash = :tokenHash
                        AND s.expiresAt > CURRENT_TIMESTAMP
                        AND s.viewCount < s.maxViews
            """)
    int claimView(
            @Param("secretId") UUID secretId,
            @Param("tokenHash") byte[] tokenHash
    ); // here the result of the query is int because, 1 means view was successfully claimed
    // 0 means con weren't satisfied
}
