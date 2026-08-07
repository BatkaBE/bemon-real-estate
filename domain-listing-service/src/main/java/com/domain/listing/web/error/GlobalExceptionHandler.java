package com.domain.listing.web.error;

import com.domain.listing.domain.model.InvalidPropertyStatusTransitionException;
import com.domain.listing.domain.model.IdempotencyConflictException;
import com.domain.listing.domain.model.PropertyNotFoundException;
import com.domain.listing.domain.model.PropertyOwnershipException;
import com.domain.listing.domain.model.StalePropertyVersionException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Converts domain and validation failures into safe RFC 9457 Problem Details. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    /** Maps validation failures to a client-safe bad request response. */
    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
    public ProblemDetail handleBadRequest(final Exception exception, final HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage(), request);
    }

    /** Maps absent listings to a not-found response. */
    @ExceptionHandler(PropertyNotFoundException.class)
    public ProblemDetail handleNotFound(
            final PropertyNotFoundException exception,
            final HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Property not found", exception.getMessage(), request);
    }

    /** Maps attempted cross-agent mutations to a forbidden response. */
    @ExceptionHandler(PropertyOwnershipException.class)
    public ProblemDetail handleForbidden(
            final PropertyOwnershipException exception,
            final HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "Property ownership required", exception.getMessage(), request);
    }

    /** Maps stale conditional requests to a precondition-failed response. */
    @ExceptionHandler(StalePropertyVersionException.class)
    public ProblemDetail handleStaleVersion(
            final StalePropertyVersionException exception,
            final HttpServletRequest request) {
        return problem(HttpStatus.PRECONDITION_FAILED, "Stale property version", exception.getMessage(), request);
    }

    /** Maps prohibited lifecycle transitions to a conflict response. */
    @ExceptionHandler({InvalidPropertyStatusTransitionException.class, IdempotencyConflictException.class})
    public ProblemDetail handleInvalidTransition(
            final InvalidPropertyStatusTransitionException exception,
            final HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Invalid property status transition", exception.getMessage(), request);
    }

    private ProblemDetail problem(
            final HttpStatus status,
            final String title,
            final String detail,
            final HttpServletRequest request) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("traceId", traceId(request));
        return problem;
    }

    private String traceId(final HttpServletRequest request) {
        final String requestId = request.getHeader("X-Request-Id");
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
    }
}
