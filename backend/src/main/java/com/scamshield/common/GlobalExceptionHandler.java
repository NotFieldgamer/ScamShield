package com.scamshield.common;

import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Turns exceptions into clean JSON. Never leaks a stack trace to the client. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Map<String, Object>> rateLimit(RateLimitException e) {
        return body(HttpStatus.TOO_MANY_REQUESTS,
                "That's a lot of postings at once. Give it about a minute, then analyze the next one.");
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(NotFoundException e) {
        return body(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> unauthorized(UnauthorizedException e) {
        return body(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> conflict(ConflictException e) {
        return body(HttpStatus.CONFLICT, e.getMessage());
    }

    // A domain-level 403 (e.g. an account too new to report). Kept distinct from the
    // AccessDeniedException handler below, which maps @PreAuthorize role denials.
    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> forbidden(ForbiddenException e) {
        return body(HttpStatus.FORBIDDEN, e.getMessage());
    }

    // A @PreAuthorize denial surfaces here as AccessDeniedException. It MUST be mapped
    // explicitly to 403 — otherwise the catch-all Exception handler below would turn a
    // legitimate authorization denial into a 500.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> accessDenied(AccessDeniedException e) {
        return body(HttpStatus.FORBIDDEN, "You do not have permission to access this resource.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream().findFirst()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .orElse("Invalid request");
        return body(HttpStatus.BAD_REQUEST, message);
    }

    // Spring MVC's own client-error exceptions must be mapped explicitly. Otherwise the catch-all
    // Exception handler below intercepts them (it runs before Spring's default resolver) and a
    // malformed body or bad param type would be reported as a 500 and logged as an error.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
        return body(HttpStatus.BAD_REQUEST, "Request body is missing or malformed.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> typeMismatch(MethodArgumentTypeMismatchException e) {
        return body(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + e.getName() + "'.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> generic(Exception e) {
        log.error("unhandled error", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something broke on our end — not your posting. Try again in a moment.");
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message));
    }
}
