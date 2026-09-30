package com.assessment.securedhelloworld.web;

import com.assessment.securedhelloworld.service.UserRegistrationService;
import com.assessment.securedhelloworld.web.dto.MessageResponse;
import com.assessment.securedhelloworld.web.dto.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class RegistrationController {

    private final UserRegistrationService registrationService;

    public RegistrationController(UserRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        registrationService.register(request.username(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(new MessageResponse("Account created"));
    }
}
