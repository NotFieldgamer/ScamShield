package com.scamshield.common;

/** Thrown when a request conflicts with existing state (e.g. email already registered); mapped to 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
