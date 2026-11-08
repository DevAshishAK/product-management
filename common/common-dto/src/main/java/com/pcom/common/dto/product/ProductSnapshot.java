package com.pcom.common.dto.product;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSnapshot(UUID id, String sku, String name, BigDecimal price, String currency, boolean active) {}
