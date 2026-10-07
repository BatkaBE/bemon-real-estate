package com.domain.payment;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.AccessDeniedException;
/** Withholds provider errors and credentials while keeping meaningful HTTP statuses. */
@RestControllerAdvice
public class PaymentErrors {
    /** Preserves explicit state and service failures. */
    @ExceptionHandler(ResponseStatusException.class) public ProblemDetail status(final ResponseStatusException failure){return ProblemDetail.forStatusAndDetail(failure.getStatusCode(),failure.getReason()==null?"Request failed":failure.getReason());}
    /** Reports malformed fields without echoing request values. */
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.web.bind.MethodArgumentNotValidException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class}) public ProblemDetail invalid(){return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Invalid request");}
    /** Preserves role denials. */
    @ExceptionHandler(AccessDeniedException.class) public ProblemDetail denied(){return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,"Insufficient permission");}
    /** Fails closed when a dependency cannot confirm a request. */
    @ExceptionHandler(Exception.class) public ProblemDetail unavailable(){return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,"Service temporarily unavailable");}
}
