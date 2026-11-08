package com.pcom.inventory.service;

import com.pcom.common.core.BusinessException;
import com.pcom.common.core.NotFoundException;
import com.pcom.common.dto.event.EventType;
import com.pcom.common.dto.event.Events;
import com.pcom.common.persistence.OutboxService;
import com.pcom.inventory.entity.InventoryItem;
import com.pcom.inventory.entity.Reservation;
import com.pcom.inventory.entity.ReservationLine;
import com.pcom.inventory.entity.ReservationStatus;
import com.pcom.inventory.repository.InventoryItemRepository;
import com.pcom.inventory.repository.ReservationRepository;
import com.pcom.inventory.web.InventoryDtos.AdjustRequest;
import com.pcom.inventory.web.InventoryDtos.InventoryResponse;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private final InventoryItemRepository items;
    private final ReservationRepository reservations;
    private final OutboxService outbox;

    public InventoryService(InventoryItemRepository items, ReservationRepository reservations, OutboxService outbox) {
        this.items = items;
        this.reservations = reservations;
        this.outbox = outbox;
    }

    /** Saga step 1: all-or-nothing reservation for an order. */
    @Transactional
    public void reserveForOrder(Events.OrderCreated event) {
        if (reservations.findByOrderId(event.orderId()).isPresent()) return; // duplicate or already released (tombstone)
        Map<UUID, Integer> needed = new TreeMap<>();
        event.lines().forEach(l -> needed.merge(l.productId(), l.quantity(), Integer::sum));
        Map<UUID, InventoryItem> locked = items.lockAll(needed.keySet()).stream()
                .collect(Collectors.toMap(InventoryItem::getProductId, Function.identity()));

        for (Map.Entry<UUID, Integer> e : needed.entrySet()) {
            InventoryItem item = locked.get(e.getKey());
            if (item == null || item.getAvailable() < e.getValue()) {
                log.info("Insufficient stock for product {} (order {})", e.getKey(), event.orderId());
                outbox.publish(EventType.INVENTORY_RESERVATION_FAILED, event.orderId(),
                        new Events.InventoryReservationFailed(event.orderId(), event.userId(), "Insufficient stock for product " + e.getKey()));
                return;
            }
        }
        Reservation reservation = new Reservation();
        reservation.setOrderId(event.orderId());
        reservation.setStatus(ReservationStatus.RESERVED);
        needed.forEach((productId, qty) -> {
            InventoryItem item = locked.get(productId);
            item.setAvailable(item.getAvailable() - qty);
            item.setReserved(item.getReserved() + qty);
            reservation.getLines().add(new ReservationLine(productId, qty));
        });
        reservations.save(reservation);
        outbox.publish(EventType.INVENTORY_RESERVED, event.orderId(),
                new Events.InventoryReserved(event.orderId(), event.userId(), event.totalAmount(), event.currency()));
    }

    /** Compensation: give stock back. Also used for orders cancelled after they were confirmed (restock). */
    @Transactional
    public void releaseForOrder(UUID orderId, String reason) {
        Reservation reservation = reservations.findByOrderId(orderId).orElse(null);
        if (reservation == null) {
            // Cancellation overtook the reservation (non-blocking retries reorder events): leave a tombstone.
            Reservation tombstone = new Reservation();
            tombstone.setOrderId(orderId);
            tombstone.setStatus(ReservationStatus.RELEASED);
            reservations.save(tombstone);
            return;
        }
        if (reservation.getStatus() == ReservationStatus.RELEASED) return;
        boolean wasCommitted = reservation.getStatus() == ReservationStatus.COMMITTED;
        Map<UUID, InventoryItem> locked = lock(reservation);
        for (ReservationLine line : reservation.getLines()) {
            InventoryItem item = locked.get(line.getProductId());
            item.setAvailable(item.getAvailable() + line.getQuantity());
            if (!wasCommitted) item.setReserved(item.getReserved() - line.getQuantity());
        }
        reservation.setStatus(ReservationStatus.RELEASED);
        outbox.publish(EventType.INVENTORY_RELEASED, orderId, new Events.InventoryReleased(orderId, reason));
    }

    /** Order confirmed: reserved stock is consumed. */
    @Transactional
    public void commit(UUID orderId) {
        reservations.findByOrderId(orderId).filter(r -> r.getStatus() == ReservationStatus.RESERVED).ifPresent(r -> {
            Map<UUID, InventoryItem> locked = lock(r);
            r.getLines().forEach(l -> {
                InventoryItem item = locked.get(l.getProductId());
                item.setReserved(item.getReserved() - l.getQuantity());
            });
            r.setStatus(ReservationStatus.COMMITTED);
        });
    }

    @Transactional
    public InventoryResponse adjust(UUID productId, AdjustRequest request) {
        InventoryItem item = items.lockAll(java.util.List.of(productId)).stream().findFirst()
                .orElseGet(() -> items.save(new InventoryItem(productId)));
        int updated = item.getAvailable() + request.delta();
        if (updated < 0) {
            throw new BusinessException("INSUFFICIENT_STOCK", "Adjustment would make stock negative", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        item.setAvailable(updated);
        outbox.publish(EventType.INVENTORY_ADJUSTED, productId,
                new Events.InventoryAdjusted(productId, request.delta(), updated, request.reason()));
        return InventoryResponse.from(item);
    }

    @Transactional(readOnly = true)
    public InventoryResponse availability(UUID productId) {
        return items.findByProductId(productId).map(InventoryResponse::from)
                .orElseThrow(() -> new NotFoundException("Inventory item", productId));
    }

    @Transactional(readOnly = true)
    public Page<InventoryResponse> list(Pageable pageable) {
        return items.findAll(pageable).map(InventoryResponse::from);
    }

    private Map<UUID, InventoryItem> lock(Reservation reservation) {
        return items.lockAll(reservation.getLines().stream().map(ReservationLine::getProductId).toList()).stream()
                .collect(Collectors.toMap(InventoryItem::getProductId, Function.identity()));
    }
}
