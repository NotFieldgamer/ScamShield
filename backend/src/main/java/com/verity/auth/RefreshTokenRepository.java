package com.verity.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Atomically revoke a single token only if it is still active, returning the number of rows
     * changed. This is the concurrency guard for rotation: the {@code AND revoked_at IS NULL}
     * predicate means that when two requests present the same token at once, exactly one UPDATE
     * matches the row (1) and the loser matches nothing (0). A caller seeing 0 knows the token
     * was already rotated and must treat it as reuse.
     */
    @Modifying(flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now "
            + "where t.id = :id and t.revokedAt is null")
    int revokeIfActive(@Param("id") Long id, @Param("now") Instant now);

    /** Revoke every still-active token in a family. Used on logout and on reuse detection. */
    @Modifying(flushAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now "
            + "where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);
}
