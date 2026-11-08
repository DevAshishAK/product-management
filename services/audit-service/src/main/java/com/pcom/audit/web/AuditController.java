package com.pcom.audit.web;

import com.pcom.audit.domain.AuditRecord;
import com.pcom.audit.domain.AuditRepository;
import com.pcom.common.core.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit")
public class AuditController {
    private final AuditRepository repository;

    public AuditController(AuditRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('audit:read')")
    public PageResponse<AuditRecord> search(@RequestParam(required = false) String aggregateId,
                                            @RequestParam(required = false) String eventType,
                                            Pageable pageable) {
        Page<AuditRecord> page = aggregateId != null ? repository.findByAggregateId(aggregateId, pageable)
                : eventType != null ? repository.findByEventType(eventType, pageable)
                : repository.findAll(pageable);
        return PageResponse.of(page, r -> r);
    }
}
