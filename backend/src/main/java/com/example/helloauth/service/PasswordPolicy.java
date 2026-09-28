package com.example.helloauth.service;

import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 12;

    public boolean isValid(String password) {
        return password != null && password.length() >= MIN_LENGTH;
    }

    public String requirementMessage() {
        return "Password must be at least " + MIN_LENGTH + " characters long.";
    }
}
