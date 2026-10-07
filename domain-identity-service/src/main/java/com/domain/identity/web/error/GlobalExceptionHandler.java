package com.domain.identity.web.error;

import com.domain.identity.application.EmailAlreadyExistsException;
import com.domain.identity.application.WeakPasswordException;
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

/** Converts registration failures into safe RFC 9457 Problem Details. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    /** Rejects invalid challenge/profile input without reflecting secret request values. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalid(final IllegalArgumentException error, final HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST,"Invalid request","Invalid or expired request",request);
    }
    /** Keeps internal service authorization errors in the same safe contract. */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ProblemDetail denied(final Exception error, final HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN,"Forbidden","Access denied",request);
    }
    /** Avoids including rejected passwords or raw JSON in API validation errors. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            final Exception exception, final Object body, final HttpHeaders headers,
            final HttpStatusCode status, final WebRequest request) {
        final HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        final ProblemDetail detail = problem(HttpStatus.valueOf(status.value()), "Invalid request",
                "The request body, parameters, or required headers are invalid", servletRequest);
        return super.handleExceptionInternal(exception, detail, headers, status, request);
    }

    /** Maps a duplicate account email to a conflict response. */
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ProblemDetail handleEmailExists(
            final EmailAlreadyExistsException exception,
            final HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Email already registered", exception.getMessage(), request);
    }

    /** Maps password-policy failures to a bad-request response. */
    @ExceptionHandler(WeakPasswordException.class)
    public ProblemDetail handleWeakPassword(
            final WeakPasswordException exception,
            final HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Password policy failed", exception.getMessage(), request);
    }

    private ProblemDetail problem(
            final HttpStatus status,
            final String title,
            final String detail,
            final HttpServletRequest request) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        final String requestId = (String) request.getAttribute("requestId");
        problem.setProperty("traceId", requestId == null ? UUID.randomUUID().toString() : requestId);
        return problem;
    }
}
