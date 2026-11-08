package com.pcom.product.web;

import com.pcom.common.core.PageResponse;
import com.pcom.common.dto.product.ProductLookupRequest;
import com.pcom.common.dto.product.ProductSnapshot;
import com.pcom.product.service.ProductService;
import com.pcom.product.web.ProductDtos.*;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@Tag(name = "Product Catalog")
public class ProductController {
    private final ProductService products;

    public ProductController(ProductService products) {
        this.products = products;
    }

    @Operation(summary = "Search products by text, category and price range")
    @GetMapping("/api/v1/products")
    @PreAuthorize("hasAuthority('product:read')")
    public PageResponse<ProductResponse> search(@RequestParam(required = false) String q,
                                                @RequestParam(required = false) UUID categoryId,
                                                @RequestParam(required = false) BigDecimal minPrice,
                                                @RequestParam(required = false) BigDecimal maxPrice,
                                                Pageable pageable) {
        return PageResponse.of(products.search(q, categoryId, minPrice, maxPrice, pageable), p -> p);
    }

    @GetMapping("/api/v1/products/{id}")
    @PreAuthorize("hasAuthority('product:read')")
    public ProductResponse get(@PathVariable UUID id) {
        return products.get(id);
    }

    @PostMapping("/api/v1/products")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('product:write')")
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return products.create(request);
    }

    @PutMapping("/api/v1/products/{id}")
    @PreAuthorize("hasAuthority('product:write')")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return products.update(id, request);
    }

    @DeleteMapping("/api/v1/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('product:write')")
    public void delete(@PathVariable UUID id) {
        products.delete(id);
    }

    @GetMapping("/api/v1/categories")
    @PreAuthorize("hasAuthority('product:read')")
    public List<CategoryResponse> categories() {
        return products.listCategories();
    }

    @PostMapping("/api/v1/categories")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('product:write')")
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
        return products.createCategory(request);
    }

    @Hidden
    @PostMapping("/internal/products/lookup")
    @PreAuthorize("hasRole('SERVICE')")
    public List<ProductSnapshot> lookup(@RequestBody ProductLookupRequest request) {
        return products.lookup(request.ids());
    }
}
