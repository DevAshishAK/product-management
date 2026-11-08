package com.pcom.invoice.web;

import com.pcom.common.core.NotFoundException;
import com.pcom.common.core.PageResponse;
import com.pcom.common.dto.PlatformHeaders;
import com.pcom.common.persistence.IdempotencyService;
import com.pcom.common.security.CurrentUser;
import com.pcom.invoice.entity.Invoice;
import com.pcom.invoice.service.InvoiceService;
import com.pcom.invoice.service.InvoicePdfGenerator;
import com.pcom.invoice.web.InvoiceDtos.InvoiceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/invoices")
@Tag(name = "Invoices")
public class InvoiceController {
    private final InvoiceService invoices;
    private final InvoicePdfGenerator pdf;
    private final IdempotencyService idempotency;

    public InvoiceController(InvoiceService invoices, InvoicePdfGenerator pdf, IdempotencyService idempotency) {
        this.invoices = invoices;
        this.pdf = pdf;
        this.idempotency = idempotency;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('invoice:read')")
    public PageResponse<InvoiceResponse> mine(Pageable pageable) {
        return PageResponse.of(invoices.listForUser(CurrentUser.id(), pageable), InvoiceResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('invoice:read')")
    public InvoiceResponse get(@PathVariable UUID id) {
        return InvoiceResponse.from(authorize(invoices.get(id)));
    }

    @GetMapping("/order/{orderId}")
    @PreAuthorize("hasAuthority('invoice:read')")
    public InvoiceResponse byOrder(@PathVariable UUID orderId) {
        return InvoiceResponse.from(authorize(invoices.getByOrder(orderId)));
    }

    @Operation(summary = "Download the invoice as PDF")
    @GetMapping(value = "/{id}/download", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAuthority('invoice:read')")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        Invoice inv = authorize(invoices.get(id));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(inv.getInvoiceNumber() + ".pdf").build().toString())
                .contentType(MediaType.APPLICATION_PDF).body(pdf.render(inv));
    }

    @Operation(summary = "Generate (or fetch) the invoice of an order. Idempotent.")
    @PostMapping("/order/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InvoiceResponse> generate(@PathVariable UUID orderId,
                                                    @RequestHeader(PlatformHeaders.IDEMPOTENCY_KEY) String key) {
        var result = idempotency.execute("invoice.generate", key + ":" + orderId, orderId, InvoiceResponse.class,
                () -> InvoiceResponse.from(invoices.generateForOrder(orderId)));
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotency-Replayed", String.valueOf(result.replayed())).body(result.body());
    }

    private Invoice authorize(Invoice i) {
        if (!CurrentUser.isPrivileged() && !i.getUserId().equals(CurrentUser.id())) throw new NotFoundException("Invoice", i.getId());
        return i;
    }
}
