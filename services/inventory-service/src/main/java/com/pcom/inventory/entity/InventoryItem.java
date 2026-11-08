package com.pcom.inventory.entity;

import com.pcom.common.persistence.AuditableEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "inventory_item")
public class InventoryItem extends AuditableEntity {
    private UUID productId;
    private int available;
    private int reserved;

    public InventoryItem(UUID productId) {
        this.productId = productId;
    }
}
