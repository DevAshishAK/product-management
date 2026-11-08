package com.pcom.product.web;

import com.pcom.product.entity.Category;
import com.pcom.product.entity.Product;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ProductDtos {
    private ProductDtos() {}

    public record ProductRequest(@NotBlank @Size(max = 64) String sku,
                                 @NotBlank @Size(max = 200) String name,
                                 @Size(max = 5000) String description,
                                 @NotNull @DecimalMin(value = "0.00") BigDecimal price,
                                 @NotBlank @Size(min = 3, max = 3) String currency,
                                 UUID categoryId) {}

    public record ProductResponse(UUID id, String sku, String name, String description, BigDecimal price,
                                  String currency, boolean active, CategoryResponse category, Instant updatedAt) {
        public static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getDescription(), p.getPrice(),
                    p.getCurrency(), p.isActive(), p.getCategory() == null ? null : CategoryResponse.from(p.getCategory()),
                    p.getUpdatedAt());
        }
    }

    public record CategoryRequest(@NotBlank @Size(max = 100) String name, @Size(max = 500) String description) {}

    public record CategoryResponse(UUID id, String name, String description) {
        public static CategoryResponse from(Category c) {
            return new CategoryResponse(c.getId(), c.getName(), c.getDescription());
        }
    }
}
