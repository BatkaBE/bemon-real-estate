package com.domain.identity.web;

import com.domain.identity.application.UserRegistrationService;
import com.domain.identity.web.dto.RegisterUserRequest;
import com.domain.identity.web.dto.UserResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for public registration and agency-admin account provisioning. */
@RestController
public class UserController {
    private final UserRegistrationService registrations;

    /** Creates the controller with the registration use case. */
    public UserController(final UserRegistrationService registrations) {
        this.registrations = registrations;
    }

    /** Registers one buyer account without allowing role selection. */
    @PostMapping("/v1/users/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody final RegisterUserRequest request) {
        final UserResponse response = UserResponse.from(
                registrations.registerBuyer(request.email(), request.password()));
        return ResponseEntity.created(URI.create("/v1/users/" + response.id())).body(response);
    }

    /** Allows an agency administrator to provision an agent account. */
    @PostMapping("/v1/admin/users/agents")
    @PreAuthorize("hasRole('AGENCY_ADMIN')")
    public ResponseEntity<UserResponse> provisionAgent(@Valid @RequestBody final RegisterUserRequest request) {
        final UserResponse response = UserResponse.from(
                registrations.provisionAgent(request.email(), request.password()));
        return ResponseEntity.created(URI.create("/v1/users/" + response.id())).body(response);
    }
}
