package com.pcom.notification.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/** Kept out of the @SpringBootApplication class so that web/JPA test slices do not try to build Feign clients. */
@Configuration(proxyBeanMethods = false)
@EnableFeignClients(basePackages = "com.pcom.notification.client")
public class FeignConfig {}
