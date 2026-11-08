package com.pcom.inventory.web;

import com.pcom.inventory.entity.InventoryItem;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public final class InventoryDtos {
    private InventoryDtos() {}

    public record AdjustRequest(int delta, @NotBlank String reason) {}

    public record InventoryResponse(UUID productId, int available, int reserved) {
        public static InventoryResponse from(InventoryItem i) {
            return new InventoryResponse(i.getProductId(), i.getAvailable(), i.getReserved());
        }
    }
}
