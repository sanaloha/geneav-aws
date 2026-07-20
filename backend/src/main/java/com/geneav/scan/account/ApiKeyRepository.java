package com.geneav.scan.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    Optional<ApiKey> findByKeyHash(String keyHash);

    List<ApiKey> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

    @Modifying
    @Query("update ApiKey k set k.lastUsedAt = :now where k.id = :id")
    void touchLastUsed(@Param("id") UUID id, @Param("now") Instant now);
}
