package com.pcom.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pcom.common.dto.event.EventType;
import com.pcom.common.dto.order.OrderView;
import com.pcom.common.persistence.OutboxService;
import com.pcom.invoice.client.OrderGateway;
import com.pcom.invoice.entity.Invoice;
import com.pcom.invoice.repository.InvoiceRepository;
import com.pcom.invoice.service.InvoicePdfGenerator;
import com.pcom.invoice.service.InvoiceService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class InvoiceServiceTest {
    InvoiceRepository invoices = Mockito.mock(InvoiceRepository.class);
    OrderGateway orders = Mockito.mock(OrderGateway.class);
    OutboxService outbox = Mockito.mock(OutboxService.class);
    InvoiceService service = new InvoiceService(invoices, orders, outbox);
    UUID orderId = UUID.randomUUID();

    OrderView order() {
        return new OrderView(orderId, UUID.randomUUID(), "CONFIRMED", new BigDecimal("25.00"), "USD", Instant.now(),
                List.of(new OrderView.OrderLineView(UUID.randomUUID(), "Widget", new BigDecimal("12.50"), 2)));
    }

    @Test
    void generatesInvoiceFromOrderAndPublishesEvent() {
        when(invoices.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(invoices.nextNumber()).thenReturn(1001L);
        when(invoices.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));
        when(orders.get(orderId)).thenReturn(order());
        Invoice inv = service.generateForOrder(orderId);
        assertThat(inv.getInvoiceNumber()).endsWith("-1001");
        assertThat(inv.getLines()).singleElement().satisfies(l -> assertThat(l.getLineTotal()).isEqualByComparingTo("25.00"));
        verify(outbox).publish(eq(EventType.INVOICE_GENERATED), eq(orderId), any());
    }

    @Test
    void secondGenerationReturnsExistingInvoice() {
        Invoice existing = new Invoice();
        when(invoices.findByOrderId(orderId)).thenReturn(Optional.of(existing));
        assertThat(service.generateForOrder(orderId)).isSameAs(existing);
        verify(orders, never()).get(any());
    }

    @Test
    void pdfIsRendered() {
        Invoice inv = new Invoice();
        inv.setInvoiceNumber("INV-2026-1001");
        inv.setOrderId(orderId); inv.setUserId(UUID.randomUUID());
        inv.setTotalAmount(BigDecimal.TEN); inv.setCurrency("USD");
        byte[] pdf = new InvoicePdfGenerator().render(inv);
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }
}
