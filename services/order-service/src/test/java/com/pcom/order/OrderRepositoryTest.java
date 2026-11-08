package com.pcom.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.pcom.common.persistence.JpaAuditingConfig;
import com.pcom.order.entity.OrderEntity;
import com.pcom.order.entity.OrderItem;
import com.pcom.order.entity.OrderStatus;
import com.pcom.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired OrderRepository orders;

    private OrderEntity save(UUID user, OrderStatus status) {
        OrderEntity o = new OrderEntity();
        o.setUserId(user);
        o.setStatus(status);
        o.setTotalAmount(new BigDecimal("20.00"));
        o.setCurrency("USD");
        o.getItems().add(new OrderItem(UUID.randomUUID(), "Widget", new BigDecimal("10.00"), 2));
        return orders.saveAndFlush(o);
    }

    @Test
    void persistsOrderWithItemsAndAuditFields() {
        OrderEntity saved = save(UUID.randomUUID(), OrderStatus.PENDING);
        OrderEntity found = orders.findById(saved.getId()).orElseThrow();
        assertThat(found.getItems()).hasSize(1);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getCreatedBy()).isEqualTo("system");
    }

    @Test
    void filtersByUserAndStatus() {
        UUID user = UUID.randomUUID();
        save(user, OrderStatus.CONFIRMED);
        save(user, OrderStatus.PENDING);
        save(UUID.randomUUID(), OrderStatus.CONFIRMED);
        assertThat(orders.findByUserId(user, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);
        assertThat(orders.findByUserIdAndStatusIn(user, List.of(OrderStatus.CONFIRMED), PageRequest.of(0, 10)).getContent()).hasSize(1);
    }
}
