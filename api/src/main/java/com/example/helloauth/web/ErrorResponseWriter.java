package com.example.helloauth.web;

import com.example.helloauth.web.dto.ApiPayloads.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Writes an {@link ApiError} straight to the servlet response.
 *
 * <p>Needed only by the filter-chain handlers, which run outside Spring MVC and so cannot rely on
 * the usual message converters. Shared between them so that a failure from the filter chain is the
 * same shape as one from a controller — a client should not have to parse two error formats.
 */
final class ErrorResponseWriter {

    private ErrorResponseWriter() {}

    static void write(
            ObjectMapper objectMapper, HttpServletResponse response, HttpStatus status, ApiError body)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
