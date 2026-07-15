package com.verity.common;

/** Thrown when credentials or a refresh token are missing, invalid, or expired; mapped to 401. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
