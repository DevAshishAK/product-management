package com.pcom.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.pcom.common.dto.event.EventSerde;
import com.pcom.common.dto.event.EventType;
import com.pcom.common.dto.event.Events;
import com.pcom.common.dto.event.Topics;
import com.pcom.common.dto.product.ProductSnapshot;
import com.pcom.order.client.ProductGateway;
import com.pcom.order.entity.OrderStatus;
import com.pcom.order.repository.OrderRepository;
import com.pcom.order.service.OrderService;
import com.pcom.order.web.OrderDtos.OrderResponse;
import com.pcom.order.web.OrderDtos.PlaceOrderRequest;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** Kafka + PostgreSQL integration test of the order side of the saga (outbox -> topic, events -> state). */
@SpringBootTest
@Testcontainers
class OrderSagaIT {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @MockitoBean ProductGateway products;
    @Autowired OrderService orders;
    @Autowired OrderRepository repository;
    @Autowired KafkaTemplate<String, String> template;
    @Autowired EventSerde serde;

    private OrderResponse placeOrder() {
        UUID productId = UUID.randomUUID();
        when(products.lookup(any())).thenReturn(List.of(new ProductSnapshot(productId, "S", "Widget", BigDecimal.TEN, "USD", true)));
        return orders.place(UUID.randomUUID(), new PlaceOrderRequest(List.of(new PlaceOrderRequest.Line(productId, 1))));
    }

    @Test
    void paymentCompletedEventConfirmsOrder() {
        OrderResponse order = placeOrder();
        String reserved = serde.write(serde.envelope(EventType.INVENTORY_RESERVED, order.id().toString(), "it",
                new Events.InventoryReserved(order.id(), order.userId(), BigDecimal.TEN, "USD")));
        String paid = serde.write(serde.envelope(EventType.PAYMENT_COMPLETED, order.id().toString(), "it",
                new Events.PaymentCompleted(UUID.randomUUID(), order.id(), order.userId(), BigDecimal.TEN, "USD", "ref")));
        template.send(Topics.INVENTORY_EVENTS, order.id().toString(), reserved);
        template.send(Topics.PAYMENT_EVENTS, order.id().toString(), paid);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(repository.findById(order.id()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CONFIRMED));
    }

    @Test
    void paymentFailedEventCancelsOrderAndPublishesCompensation() {
        OrderResponse order = placeOrder();
        template.send(Topics.PAYMENT_EVENTS, order.id().toString(), serde.write(serde.envelope(EventType.PAYMENT_FAILED,
                order.id().toString(), "it", new Events.PaymentFailed(UUID.randomUUID(), order.id(), order.userId(), "declined"))));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var o = repository.findById(order.id()).orElseThrow();
            assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(o.getCancelReason()).contains("declined");
        });
    }
}
