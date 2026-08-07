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

/** Converts registration failures into safe RFC 9457 Problem Details. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    /** Maps malformed registration requests to bad-request responses. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(
            final MethodArgumentNotValidException exception,
            final HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid registration request", "One or more fields are invalid", request);
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
        problem.setProperty("traceId", UUID.randomUUID().toString());
        return problem;
    }
}
