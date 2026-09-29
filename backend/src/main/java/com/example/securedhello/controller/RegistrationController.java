package com.example.securedhello.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.securedhello.controller.dto.RegistrationRequest;
import com.example.securedhello.controller.dto.RegistrationResponse;
import com.example.securedhello.entity.User;
import com.example.securedhello.service.RegistrationService;

/**
 * Registration endpoint. Accepts a Username, Email, and password, delegates to
 * {@link RegistrationService}, and returns 201 with the created account's
 * non-sensitive fields. Validation failures yield 400 and duplicate
 * Username/Email yield 409, both handled by {@link RegistrationExceptionHandler}.
 */
@RestController
@RequestMapping("/api")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegistrationRequest request) {
        User created = registrationService.register(
                request.username(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(RegistrationResponse.from(created));
    }
}
