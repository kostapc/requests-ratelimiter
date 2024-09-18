package com.arnoldgalovics.blog.ratelimiterfeign;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.google.common.util.concurrent.AtomicDouble;
import net.c0f3.labs.ratelimiter.RequestsBottleneckGate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
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

    private static final UUID uuid = UUID.fromString("828bc3cb-52f0-482b-8247-d3db5c87c941");

    @BeforeEach
    public void prepareMock() {
        String responseBody = "{ \"sessionId\": \"" + uuid + "\", \"valid\": true}";

        USER_SESSION_SERVICE.stubFor(
            get(urlPathEqualTo("/user-sessions/validate"))
                .withQueryParam("sessionId", equalTo(uuid.toString()))
                .willReturn(aResponse()
                    .withBody(responseBody)
                    .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                    .withFixedDelay(10)
                )
        );
    }

    @Test
    public void testRateLimiterWorks() {

        final int count = 6;

        ExecutorService executorService = Executors.newFixedThreadPool(count);

        List<Future<UserSessionValidationResponse>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            var f = executorService.submit(() -> userSessionClient.validateSession(uuid));
            futures.add(f);
        }
        var counter = new AtomicInteger(1);
        Assertions.assertThrows(RuntimeException.class, () -> futures.forEach(f -> {
            try {
                f.get();
                System.out.println("resp " + counter.getAndIncrement());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }));
    }

    @Test
    public void testRateLimiterWithBucketWorks() {

        var count = 10;

        ExecutorService executorService = Executors.newFixedThreadPool(count);

        var limitator = new RequestsBottleneckGate<UserSessionValidationResponse>();

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Future<?> f = executorService.submit(
                () -> limitator.doRequest(
                    () -> userSessionClient.validateSession(uuid)
                )
            );
            futures.add(f);
        }
        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    public void testHightPressureRateLimiterWithBucketWorks() {
        var totalCount = 200;
        var executorService = new ThreadPoolExecutor(
            50, 50, 0L,
            TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(totalCount),
            Executors.defaultThreadFactory(),
            // invocation by caller thread
            (r, exe) -> {
                org.assertj.core.api.Assertions.fail("rejected execution call");
            }
        );

        var limitator = new RequestsBottleneckGate<UserSessionValidationResponse>();

        var log = new ArrayList<>(totalCount);
        var ave = new AtomicDouble(0);
        List<Future<UserSessionValidationResponse>> futures = new ArrayList<>();
        for (int i = 0; i < totalCount; i++) {
            final int ii = i;
            var f = executorService.submit(
                () -> {
                    var time = System.currentTimeMillis();
                    var res = limitator.doRequest(
                        () -> userSessionClient.validateSession(uuid)
                    );
                    time = System.currentTimeMillis() - time;
                    log.add(ii + ") " + (time/1000) + " sec.\t" + time);
                    ave.set((time + ave.get() * ii)/(ii+1));
                    return res;
                }
            );
            futures.add(f);
        }
        var failFlag = new AtomicBoolean(false);
        for (Future<UserSessionValidationResponse> f : futures) {
            try {
                var res = f.get();
                Assertions.assertNotNull(res);
            } catch (Exception e) {
                e.printStackTrace();
                failFlag.set(true);
                break;
            }
        }
        log.forEach(System.out::println);
        System.out.println("> total count: " + log.size() + "; avg time: " + ave.get());
        Assertions.assertFalse(failFlag.get(), "one of executions failed");
    }
}