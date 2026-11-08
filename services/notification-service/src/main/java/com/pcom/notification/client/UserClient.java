package com.pcom.notification.client;

import com.pcom.common.dto.user.UserContact;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service")
public interface UserClient {
    @GetMapping("/internal/users/{id}/contact")
    UserContact contact(@PathVariable("id") UUID id);
}
