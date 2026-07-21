package com.geneav.scan.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Burns every outstanding token for an account. Called both when a new reset
     * is requested and after one is redeemed, so a leaked older link is dead.
     */
    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now where t.accountId = :accountId and t.usedAt is null")
    void invalidateOutstanding(@Param("accountId") UUID accountId, @Param("now") Instant now);

    /** Most recent issue for an account, used to enforce the per-account cooldown. */
    Optional<PasswordResetToken> findTopByAccountIdOrderByCreatedAtDesc(UUID accountId);

    /**
     * Deletes tokens that expired before the cutoff. Spent tokens expire on
     * schedule too, so this covers both used and simply-abandoned rows.
     */
    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
