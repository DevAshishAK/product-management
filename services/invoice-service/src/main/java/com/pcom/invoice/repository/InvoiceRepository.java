package com.pcom.invoice.repository;

import com.pcom.invoice.entity.Invoice;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    Optional<Invoice> findByOrderId(UUID orderId);
    Page<Invoice> findByUserId(UUID userId, Pageable pageable);

    @Query(value = "select nextval('invoice_number_seq')", nativeQuery = true)
    long nextNumber();
}
