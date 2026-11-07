package com.campuson.api.global.error;

import java.time.Duration;

/** 429 TOO_MANY_REQUESTS. 핸들러가 {@code Retry-After}(초, 올림)를 함께 보낸다. */
public class RateLimitedException extends BusinessException {
    private final long retryAfterSeconds;

    public RateLimitedException(Duration retryAfter) {
        super(ErrorCode.TOO_MANY_REQUESTS);
        long millis = Math.max(retryAfter.toMillis(), 1);
        this.retryAfterSeconds = (millis + 999) / 1000;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
