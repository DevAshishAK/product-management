package com.pcom.product.service;

import com.pcom.common.core.ConflictException;
import com.pcom.common.core.NotFoundException;
import com.pcom.common.dto.product.ProductSnapshot;
import com.pcom.product.entity.Category;
import com.pcom.product.entity.Product;
import com.pcom.product.repository.CategoryRepository;
import com.pcom.product.repository.ProductRepository;
import com.pcom.product.web.ProductDtos.*;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {
    private final ProductRepository products;
    private final CategoryRepository categories;

    public ProductService(ProductRepository products, CategoryRepository categories) {
        this.products = products;
        this.categories = categories;
    }

    @Transactional
    public ProductResponse create(ProductRequest r) {
        if (products.existsBySku(r.sku())) throw new ConflictException("SKU_EXISTS", "SKU already exists: " + r.sku());
        Product p = new Product();
        apply(p, r);
        return ProductResponse.from(products.save(p));
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest r) {
        Product p = find(id);
        if (!p.getSku().equals(r.sku()) && products.existsBySku(r.sku())) {
            throw new ConflictException("SKU_EXISTS", "SKU already exists: " + r.sku());
        }
        apply(p, r);
        return ProductResponse.from(p);
    }

    /** Soft delete keeps historical orders and invoices resolvable. */
    @Transactional
    public void delete(UUID id) {
        find(id).setActive(false);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return ProductResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> search(String q, UUID categoryId, BigDecimal min, BigDecimal max, Pageable pageable) {
        return products.findAll(spec(q, categoryId, min, max), pageable).map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public List<ProductSnapshot> lookup(Collection<UUID> ids) {
        return products.findAllByIdIn(ids).stream()
                .map(p -> new ProductSnapshot(p.getId(), p.getSku(), p.getName(), p.getPrice(), p.getCurrency(), p.isActive()))
                .toList();
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest r) {
        if (categories.existsByNameIgnoreCase(r.name())) throw new ConflictException("CATEGORY_EXISTS", "Category already exists");
        Category c = new Category();
        c.setName(r.name());
        c.setDescription(r.description());
        return CategoryResponse.from(categories.save(c));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categories.findAll().stream().map(CategoryResponse::from).toList();
    }

    public static Specification<Product> spec(String q, UUID categoryId, BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isTrue(root.get("active")));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("name")), like), cb.like(cb.lower(root.get("sku")), like)));
            }
            if (categoryId != null) ps.add(cb.equal(root.get("category").get("id"), categoryId));
            if (min != null) ps.add(cb.greaterThanOrEqualTo(root.get("price"), min));
            if (max != null) ps.add(cb.lessThanOrEqualTo(root.get("price"), max));
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }

    private void apply(Product p, ProductRequest r) {
        p.setSku(r.sku());
        p.setName(r.name());
        p.setDescription(r.description());
        p.setPrice(r.price());
        p.setCurrency(r.currency().toUpperCase());
        p.setCategory(r.categoryId() == null ? null
                : categories.findById(r.categoryId()).orElseThrow(() -> new NotFoundException("Category", r.categoryId())));
    }

    private Product find(UUID id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Product", id));
    }
}
