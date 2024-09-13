package net.c0f3.labs.ratelimiter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

public class RequestsBottleneckGate<R> {
    private final Bucket bucket;

    public RequestsBottleneckGate() {
        Refill refill = Refill.intervally(5, Duration.ofSeconds(1));
        Bandwidth limit = Bandwidth.classic(5, refill);
        this.bucket = Bucket.builder()
            .addLimit(limit)
            .build();
    }

    public Optional<R> doRequest(Supplier<R> request) {
        try {
            if (bucket.asBlocking().tryConsume(1, Duration.ofSeconds(10))) {
                return Optional.ofNullable(request.get());
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }
}
