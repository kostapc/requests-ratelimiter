package net.c0f3.labs.ratelimiter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * <a href="https://bucket4j.com/8.14.0/toc.html">Bucket4j docs</a>
 *
 * @param <R> - call result type
 */
public class RequestsBottleneckGate<R> {

    private static final int TOTAL_TIMEOUT_MILLS = 1000 * 60 - 500;
    private final Bucket bucket;

    public RequestsBottleneckGate() {
        Refill refill = Refill.intervally(5, Duration.ofSeconds(1));
        Bandwidth limit = Bandwidth.classic(5, refill);
        this.bucket = Bucket.builder()
            .addLimit(limit)
            .build();
    }

    public R doRequest(Supplier<R> request) {
        var bb = bucket.asBlocking();
        try {
            bb.tryConsume(1, Duration.ofMillis(TOTAL_TIMEOUT_MILLS));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return request.get();

        /*var start = System.currentTimeMillis();
        var passedTime = 0L;
        var counter = 0;
        while (passedTime < TOTAL_TIMEOUT_MILLS) {
            counter++;
            try {
                if (bb.tryConsume(1, Duration.ofSeconds(1))) {
                    return request.get();
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("interrupted on retry " + counter, e);
            } catch (Exception e) {
                throw new RuntimeException("failed on retry " + counter, e);
            }
            passedTime = System.currentTimeMillis() - start;
        }
        throw new RuntimeException("call unavailable");*/
    }

}
