package com.pcom.invoice.service;

import com.pcom.common.core.NotFoundException;
import com.pcom.common.dto.event.EventType;
import com.pcom.common.dto.event.Events;
import com.pcom.common.dto.order.OrderView;
import com.pcom.common.persistence.OutboxService;
import com.pcom.invoice.client.OrderGateway;
import com.pcom.invoice.entity.Invoice;
import com.pcom.invoice.entity.InvoiceLine;
import com.pcom.invoice.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.time.Year;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {
    private final InvoiceRepository invoices;
    private final OrderGateway orders;
    private final OutboxService outbox;

    public InvoiceService(InvoiceRepository invoices, OrderGateway orders, OutboxService outbox) {
        this.invoices = invoices;
        this.orders = orders;
        this.outbox = outbox;
    }

    /** Idempotent by order: a second call returns the existing invoice (unique order_id backs this up). */
    @Transactional
    public Invoice generateForOrder(UUID orderId) {
        return invoices.findByOrderId(orderId).orElseGet(() -> create(orders.get(orderId)));
    }

    private Invoice create(OrderView order) {
        Invoice inv = new Invoice();
        inv.setInvoiceNumber("INV-" + Year.now() + "-" + invoices.nextNumber());
        inv.setOrderId(order.id());
        inv.setUserId(order.userId());
        inv.setTotalAmount(order.totalAmount());
        inv.setCurrency(order.currency());
        order.lines().forEach(l -> inv.getLines().add(new InvoiceLine(l.productName(), l.unitPrice(), l.quantity(),
                l.unitPrice().multiply(BigDecimal.valueOf(l.quantity())))));
        inv = invoices.save(inv);
        outbox.publish(EventType.INVOICE_GENERATED, inv.getOrderId(), new Events.InvoiceGenerated(
                inv.getId(), inv.getInvoiceNumber(), inv.getOrderId(), inv.getUserId(), inv.getTotalAmount(), inv.getCurrency()));
        return inv;
    }

    @Transactional(readOnly = true)
    public Invoice get(UUID id) {
        return invoices.findById(id).orElseThrow(() -> new NotFoundException("Invoice", id));
    }

    @Transactional(readOnly = true)
    public Invoice getByOrder(UUID orderId) {
        return invoices.findByOrderId(orderId).orElseThrow(() -> new NotFoundException("Invoice for order", orderId));
    }

    @Transactional(readOnly = true)
    public Page<Invoice> listForUser(UUID userId, Pageable pageable) {
        return invoices.findByUserId(userId, pageable);
    }
}
