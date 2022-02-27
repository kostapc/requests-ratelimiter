package com.arnoldgalovics.blog.ratelimiterfeign;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "user-session-service", url = "http://localhost:8081")
public interface UserSessionClient {
    @GetMapping("/user-sessions/validate")
    @RateLimiter(name = "validateSession")
    UserSessionValidationResponse validateSession(@RequestParam UUID sessionId);
}