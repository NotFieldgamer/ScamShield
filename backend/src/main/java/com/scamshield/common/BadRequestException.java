package com.scamshield.common;

/**
 * The request was well-formed enough to reach the handler but cannot be processed — e.g. a bulk
 * upload with no CSV file, or one whose first column holds no postings. Maps to 400 with a clean,
 * actionable message and never a stack trace.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
