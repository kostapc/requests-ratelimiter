package com.arnoldgalovics.blog.ratelimiterfeign;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@SpringBootTest({"server.port:0"})
class RateLimiterTest {
    @RegisterExtension
    static WireMockExtension USER_SESSION_SERVICE = WireMockExtension.newInstance()
            .options(WireMockConfiguration.wireMockConfig().port(8081))
            .build();

    @Autowired
    private UserSessionClient userSessionClient;

    @Test
    public void testRateLimiterWorks() throws Exception {
        String responseBody = "{ \"sessionId\": \"828bc3cb-52f0-482b-8247-d3db5c87c941\", \"valid\": true}";

        String uuidString = "828bc3cb-52f0-482b-8247-d3db5c87c941";
        UUID uuid = UUID.fromString(uuidString);

        USER_SESSION_SERVICE.stubFor(get(urlPathEqualTo("/user-sessions/validate"))
                .withQueryParam("sessionId", equalTo(uuidString))
                .willReturn(aResponse().withBody(responseBody).withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE).withFixedDelay(500)));

        ExecutorService executorService = Executors.newFixedThreadPool(10);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Future<?> f = executorService.submit(() -> userSessionClient.validateSession(uuid));
            futures.add(f);
        }
        futures.forEach(f -> {
            try {
                f.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}