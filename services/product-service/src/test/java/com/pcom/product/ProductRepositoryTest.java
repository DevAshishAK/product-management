package com.pcom.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.pcom.common.persistence.JpaAuditingConfig;
import com.pcom.product.entity.Category;
import com.pcom.product.entity.Product;
import com.pcom.product.repository.CategoryRepository;
import com.pcom.product.repository.ProductRepository;
import com.pcom.product.service.ProductService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductRepositoryTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;

    @Test
    void searchFiltersByTextCategoryPriceAndActive() {
        Category c = new Category();
        c.setName("Tools");
        c = categories.saveAndFlush(c);
        save("HAM-1", "Hammer", "12.00", c, true);
        save("SAW-1", "Saw", "30.00", c, true);
        save("OLD-1", "Old hammer", "5.00", c, false);

        var page = PageRequest.of(0, 10);
        assertThat(products.findAll(ProductService.spec("hammer", null, null, null), page)).hasSize(1);
        assertThat(products.findAll(ProductService.spec(null, c.getId(), new BigDecimal("20"), null), page)).hasSize(1);
        assertThat(products.findAll(ProductService.spec(null, null, null, null), page)).hasSize(2);
    }

    private void save(String sku, String name, String price, Category c, boolean active) {
        Product p = new Product();
        p.setSku(sku); p.setName(name); p.setPrice(new BigDecimal(price)); p.setCurrency("USD"); p.setCategory(c); p.setActive(active);
        products.saveAndFlush(p);
    }
}
