package com.scamshield.common;

/**
 * The caller is authenticated but not permitted to perform this action for a reason other than
 * their role — e.g. an account too new to file a report. Distinct from Spring Security's
 * {@code AccessDeniedException} (which covers {@code @PreAuthorize} role denials); both map to 403.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
