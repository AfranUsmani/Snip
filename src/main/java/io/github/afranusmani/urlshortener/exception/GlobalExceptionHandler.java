package io.github.afranusmani.urlshortener.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

/**
 * Translates exceptions into consistent {@link ApiError} responses so that
 * clients always receive a predictable JSON error shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ShortCodeNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ShortCodeNotFoundException ex,
                                                   HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, List.of(ex.getMessage()), request);
    }

    @ExceptionHandler(LinkExpiredException.class)
    public ResponseEntity<ApiError> handleExpired(LinkExpiredException ex,
                                                  HttpServletRequest request) {
        return build(HttpStatus.GONE, List.of(ex.getMessage()), request);
    }

    @ExceptionHandler(AliasAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleAliasTaken(AliasAlreadyExistsException ex,
                                                     HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, List.of(ex.getMessage()), request);
    }

    /**
     * Backstop for the unique short-code index: covers the race where a custom
     * alias is taken between the availability check and insert (and the rare case
     * of a generated code clashing with an existing alias).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleConflict(DataIntegrityViolationException ex,
                                                   HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, List.of("Short code already in use"), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                      HttpServletRequest request) {
        List<String> messages = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, messages, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex,
                                                          HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, List.of(ex.getMessage()), request);
    }

    @ExceptionHandler(UnsafeUrlException.class)
    public ResponseEntity<ApiError> handleUnsafeUrl(UnsafeUrlException ex,
                                                    HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, List.of(ex.getMessage()), request);
    }

    /**
     * Backstop so clients always receive the {@link ApiError} shape. Framework
     * exceptions that already carry an HTTP status (e.g. an unknown route's 404)
     * keep that status; anything genuinely unexpected is logged and returned as a
     * 500 rather than leaking a stack trace via the default error page.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            return build(status, List.of(status.getReasonPhrase()), request);
        }
        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, List.of("Something went wrong"), request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status,
                                           List<String> messages,
                                           HttpServletRequest request) {
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                messages,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(body);
    }
}
