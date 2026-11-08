package com.pcom.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pcom.common.core.CoreAutoConfiguration;
import com.pcom.common.persistence.IdempotencyService;
import com.pcom.common.security.PcomSecurityAutoConfiguration;
import com.pcom.order.entity.OrderStatus;
import com.pcom.order.service.OrderService;
import com.pcom.order.web.OrderController;
import com.pcom.order.web.OrderDtos.OrderResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
@Import({PcomSecurityAutoConfiguration.class, CoreAutoConfiguration.class})
class OrderControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean OrderService orders;
    @MockitoBean IdempotencyService idempotency;
    @MockitoBean JwtDecoder jwtDecoder;

    UUID userId = UUID.randomUUID();
    String body = "{\"items\":[{\"productId\":\"" + UUID.randomUUID() + "\",\"quantity\":2}]}";

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    void placeOrderRequiresAuthority() throws Exception {
        mvc.perform(post("/api/v1/orders").header("Idempotency-Key", "k1").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().jwt(j -> j.subject(userId.toString())).authorities(new SimpleGrantedAuthority("order:read"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void placeOrderReturnsCreated() throws Exception {
        OrderResponse resp = new OrderResponse(UUID.randomUUID(), userId, OrderStatus.PENDING, BigDecimal.TEN, "USD", null,
                Instant.now(), Instant.now(), List.of());
        when(idempotency.execute(eq("order.place"), anyString(), any(), eq(OrderResponse.class), any()))
                .thenReturn(new IdempotencyService.Result<>(resp, false));
        mvc.perform(post("/api/v1/orders").header("Idempotency-Key", "k1").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().jwt(j -> j.subject(userId.toString())).authorities(new SimpleGrantedAuthority("order:write"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void missingIdempotencyKeyIsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().jwt(j -> j.subject(userId.toString())).authorities(new SimpleGrantedAuthority("order:write"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptyItemsFailValidation() throws Exception {
        mvc.perform(post("/api/v1/orders").header("Idempotency-Key", "k1").contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}")
                        .with(jwt().jwt(j -> j.subject(userId.toString())).authorities(new SimpleGrantedAuthority("order:write"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
