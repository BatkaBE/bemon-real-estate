package com.domain.identity.web.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Returns the API's Problem Details format for failures raised before controller advice. */
@Component
public class ApiSecurityErrors implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper objectMapper;

    /** Uses Spring's mapper, including its ProblemDetail JSON support. */
    public ApiSecurityErrors(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Challenges unauthenticated API callers without redirecting them to HTML login. */
    @Override
    public void commence(final HttpServletRequest request, final HttpServletResponse response,
            final AuthenticationException exception) throws IOException {
        response.setHeader("WWW-Authenticate", "Bearer");
        write(request, response, HttpStatus.UNAUTHORIZED, "A valid bearer access token is required");
    }

    /** Explains authorization failure without exposing security internals. */
    @Override
    public void handle(final HttpServletRequest request, final HttpServletResponse response,
            final AccessDeniedException exception) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "You do not have permission to perform this operation");
    }

    /** Serializes a safe error envelope sharing the request's trace identifier. */
    private void write(final HttpServletRequest request, final HttpServletResponse response,
            final HttpStatus status, final String message) throws IOException {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, message);
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("traceId", request.getAttribute("requestId"));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
