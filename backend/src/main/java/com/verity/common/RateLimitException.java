package com.verity.common;

/** Thrown when a caller exceeds the rate limit; mapped to HTTP 429. */
public class RateLimitException extends RuntimeException {
    public RateLimitException(String message) {
        super(message);
    }
}
