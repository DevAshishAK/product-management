package com.pcom.common.dto.product;

import java.util.Collection;
import java.util.UUID;

public record ProductLookupRequest(Collection<UUID> ids) {}
