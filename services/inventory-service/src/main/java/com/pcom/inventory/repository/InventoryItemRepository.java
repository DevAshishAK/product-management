package com.pcom.inventory.repository;

import com.pcom.inventory.entity.InventoryItem;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {
    Optional<InventoryItem> findByProductId(UUID productId);

    /** Rows are locked in product-id order so concurrent multi-item reservations cannot deadlock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.productId in :ids order by i.productId")
    List<InventoryItem> lockAll(@Param("ids") Collection<UUID> ids);
}
