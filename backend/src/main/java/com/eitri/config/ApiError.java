package com.eitri.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/** Error body of every API response: exactly {@code {"message": "..."}}. */
public record ApiError(String message) {

    /** A generic message for the status that reveals nothing about the cause. */
    public static ApiError of(HttpStatusCode status) {
        HttpStatus known = HttpStatus.resolve(status.value());
        return new ApiError(known != null ? known.getReasonPhrase() : "Error");
    }
}
