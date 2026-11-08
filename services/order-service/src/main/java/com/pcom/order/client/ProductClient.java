package com.pcom.order.client;

import com.pcom.common.dto.product.ProductLookupRequest;
import com.pcom.common.dto.product.ProductSnapshot;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "product-service")
public interface ProductClient {
    @PostMapping("/internal/products/lookup")
    List<ProductSnapshot> lookup(@RequestBody ProductLookupRequest request);
}
