package com.pcom.inventory.web;

import com.pcom.common.core.PageResponse;
import com.pcom.inventory.service.InventoryService;
import com.pcom.inventory.web.InventoryDtos.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventory")
public class InventoryController {
    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('inventory:read')")
    public PageResponse<InventoryResponse> list(Pageable pageable) {
        return PageResponse.of(inventory.list(pageable), i -> i);
    }

    @GetMapping("/{productId}")
    @PreAuthorize("hasAuthority('inventory:read')")
    public InventoryResponse availability(@PathVariable UUID productId) {
        return inventory.availability(productId);
    }

    @PutMapping("/{productId}/adjust")
    @PreAuthorize("hasAuthority('inventory:write')")
    public InventoryResponse adjust(@PathVariable UUID productId, @Valid @RequestBody AdjustRequest request) {
        return inventory.adjust(productId, request);
    }
}
