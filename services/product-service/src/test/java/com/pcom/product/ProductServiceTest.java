package com.pcom.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.pcom.common.core.ConflictException;
import com.pcom.common.core.NotFoundException;
import com.pcom.product.entity.Product;
import com.pcom.product.repository.CategoryRepository;
import com.pcom.product.repository.ProductRepository;
import com.pcom.product.service.ProductService;
import com.pcom.product.web.ProductDtos.ProductRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProductServiceTest {
    ProductRepository products = Mockito.mock(ProductRepository.class);
    CategoryRepository categories = Mockito.mock(CategoryRepository.class);
    ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(products, categories);
        when(products.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));
    }

    ProductRequest req(String sku) {
        return new ProductRequest(sku, "Widget", "desc", new BigDecimal("9.99"), "usd", null);
    }

    @Test
    void createNormalisesCurrency() {
        assertThat(service.create(req("W-1")).currency()).isEqualTo("USD");
    }

    @Test
    void createRejectsDuplicateSku() {
        when(products.existsBySku("W-1")).thenReturn(true);
        assertThatThrownBy(() -> service.create(req("W-1"))).isInstanceOf(ConflictException.class);
    }

    @Test
    void deleteIsSoft() {
        Product p = new Product();
        p.setActive(true);
        UUID id = UUID.randomUUID();
        when(products.findById(id)).thenReturn(Optional.of(p));
        service.delete(id);
        assertThat(p.isActive()).isFalse();
    }

    @Test
    void getUnknownProductFails() {
        when(products.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void lookupMapsToSnapshots() {
        Product p = new Product();
        p.setSku("W-1"); p.setName("Widget"); p.setPrice(BigDecimal.ONE); p.setCurrency("USD");
        when(products.findAllByIdIn(any())).thenReturn(List.of(p));
        assertThat(service.lookup(List.of(UUID.randomUUID()))).singleElement().satisfies(s -> assertThat(s.sku()).isEqualTo("W-1"));
    }
}
