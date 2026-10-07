package com.domain.listing.web.error;

import com.domain.listing.domain.model.InvalidPropertyStatusTransitionException;
import com.domain.listing.domain.model.IdempotencyConflictException;
import com.domain.listing.domain.model.PropertyNotFoundException;
import com.domain.listing.domain.model.PropertyOwnershipException;
import com.domain.listing.domain.model.StalePropertyVersionException;
import com.domain.listing.domain.model.MediaStorageUnavailableException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.OptimisticLockingFailureException;
import jakarta.persistence.OptimisticLockException;

/** Converts domain and validation failures into safe RFC 9457 Problem Details. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    /** Returns a deliberate service-unavailable response when object storage is disabled. */
    @ExceptionHandler(MediaStorageUnavailableException.class)
    public ProblemDetail handleMediaUnavailable(
            final MediaStorageUnavailableException exception, final HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Media storage unavailable", exception.getMessage(), request);
    }

    /** Maps invalid filters and cursors to a client-safe bad request response. */
    @ExceptionHandler(IllegalArgumentException.class)
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
    @ExceptionHandler({StalePropertyVersionException.class,
            OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ProblemDetail handleStaleVersion(
            final Exception exception,
            final HttpServletRequest request) {
        return problem(HttpStatus.PRECONDITION_FAILED, "Stale property version",
                "The listing changed; fetch its current ETag before retrying", request);
    }

    /** Maps prohibited lifecycle transitions to a conflict response. */
    @ExceptionHandler(InvalidPropertyStatusTransitionException.class)
    public ProblemDetail handleInvalidTransition(
            final InvalidPropertyStatusTransitionException exception,
            final HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Invalid property status transition", exception.getMessage(), request);
    }

    /** Maps changed reuse of an idempotency key to a conflict response. */
    @ExceptionHandler(IdempotencyConflictException.class)
    public ProblemDetail handleIdempotencyConflict(
            final IdempotencyConflictException exception, final HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Idempotency key conflict", exception.getMessage(), request);
    }

    /** Normalizes Spring's malformed JSON, header, conversion, and validation failures. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            final Exception exception, final Object body, final HttpHeaders headers,
            final HttpStatusCode status, final WebRequest request) {
        final HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        final ProblemDetail detail = problem(HttpStatus.valueOf(status.value()), "Invalid request",
                "The request body, parameters, or required headers are invalid", servletRequest);
        return super.handleExceptionInternal(exception, detail, headers, status, request);
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
        final String requestId = (String) request.getAttribute("requestId");
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
    }
}
