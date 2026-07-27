package com.geneav.scan.marketplace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MarketplaceEventRepository extends JpaRepository<MarketplaceEvent, UUID> {

    boolean existsByEventId(UUID eventId);
}
