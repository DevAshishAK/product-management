package com.pcom.order.repository;

import com.pcom.order.entity.OrderEntity;
import com.pcom.order.entity.OrderStatus;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    Page<OrderEntity> findByUserId(UUID userId, Pageable pageable);
    Page<OrderEntity> findByUserIdAndStatusIn(UUID userId, Collection<OrderStatus> statuses, Pageable pageable);
}
