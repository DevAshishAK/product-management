package com.pcom.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pcom.common.core.BusinessException;
import com.pcom.common.dto.event.EventType;
import com.pcom.common.dto.event.Events;
import com.pcom.common.persistence.OutboxService;
import com.pcom.inventory.entity.InventoryItem;
import com.pcom.inventory.entity.Reservation;
import com.pcom.inventory.entity.ReservationLine;
import com.pcom.inventory.entity.ReservationStatus;
import com.pcom.inventory.repository.InventoryItemRepository;
import com.pcom.inventory.repository.ReservationRepository;
import com.pcom.inventory.service.InventoryService;
import com.pcom.inventory.web.InventoryDtos.AdjustRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class InventoryServiceTest {
    InventoryItemRepository items = Mockito.mock(InventoryItemRepository.class);
    ReservationRepository reservations = Mockito.mock(ReservationRepository.class);
    OutboxService outbox = Mockito.mock(OutboxService.class);
    InventoryService service;
    UUID productId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    InventoryItem item;

    @BeforeEach
    void setUp() {
        service = new InventoryService(items, reservations, outbox);
        item = new InventoryItem(productId);
        item.setAvailable(10);
        when(items.lockAll(any())).thenReturn(List.of(item));
    }

    Events.OrderCreated order(int qty) {
        return new Events.OrderCreated(orderId, UUID.randomUUID(),
                List.of(new Events.OrderLine(productId, "W", BigDecimal.ONE, qty)), BigDecimal.TEN, "USD");
    }

    @Test
    void reserveMovesStockAndEmitsInventoryReserved() {
        service.reserveForOrder(order(4));
        assertThat(item.getAvailable()).isEqualTo(6);
        assertThat(item.getReserved()).isEqualTo(4);
        verify(outbox).publish(eq(EventType.INVENTORY_RESERVED), eq(orderId), any());
    }

    @Test
    void reserveFailsWholeOrderWhenStockIsShort() {
        service.reserveForOrder(order(11));
        assertThat(item.getAvailable()).isEqualTo(10);
        verify(outbox).publish(eq(EventType.INVENTORY_RESERVATION_FAILED), eq(orderId), any());
        verify(reservations, never()).save(any());
    }

    @Test
    void reserveIsIdempotentPerOrder() {
        when(reservations.findByOrderId(orderId)).thenReturn(Optional.of(new Reservation()));
        service.reserveForOrder(order(1));
        verify(outbox, never()).publish(any(), any(), any());
    }

    @Test
    void releaseRestoresReservedStock() {
        item.setAvailable(6); item.setReserved(4);
        Reservation r = new Reservation();
        r.setOrderId(orderId); r.setStatus(ReservationStatus.RESERVED);
        r.getLines().add(new ReservationLine(productId, 4));
        when(reservations.findByOrderId(orderId)).thenReturn(Optional.of(r));
        service.releaseForOrder(orderId, "cancelled");
        assertThat(item.getAvailable()).isEqualTo(10);
        assertThat(item.getReserved()).isZero();
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        verify(outbox).publish(eq(EventType.INVENTORY_RELEASED), eq(orderId), any());
    }

    @Test
    void releaseBeforeReservationLeavesTombstone() {
        when(reservations.findByOrderId(orderId)).thenReturn(Optional.empty());
        service.releaseForOrder(orderId, "cancelled");
        verify(reservations).save(any(Reservation.class));
    }

    @Test
    void commitConsumesReservedStock() {
        item.setAvailable(6); item.setReserved(4);
        Reservation r = new Reservation();
        r.setStatus(ReservationStatus.RESERVED);
        r.getLines().add(new ReservationLine(productId, 4));
        when(reservations.findByOrderId(orderId)).thenReturn(Optional.of(r));
        service.commit(orderId);
        assertThat(item.getReserved()).isZero();
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.COMMITTED);
    }

    @Test
    void adjustRejectsNegativeStock() {
        assertThatThrownBy(() -> service.adjust(productId, new AdjustRequest(-11, "shrinkage"))).isInstanceOf(BusinessException.class);
        assertThat(service.adjust(productId, new AdjustRequest(5, "restock")).available()).isEqualTo(15);
    }
}
