package com.example.authapp.service;

import org.springframework.http.HttpStatus;

public class TooManyAttemptsException extends ApiException {

    public TooManyAttemptsException() {
        super(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Please try again later.");
    }
}
