package com.example.authapp.service;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    /** BCrypt silently truncates beyond 72 bytes, so reject longer input instead. */
    public static final int MAX_BYTES = 72;

    public void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Password must be at least " + MIN_LENGTH + " characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Password must be at most " + MAX_BYTES + " bytes");
        }
    }
}
