package com.eitri.auth;

import com.eitri.config.ApiError;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

@RestControllerAdvice(assignableTypes = AuthController.class)
class LoginRequestBodyAdvice extends RequestBodyAdviceAdapter {

    private static final int MAX_LOGIN_BODY_BYTES = 4 * 1024;
    private static final Logger LOGGER = LoggerFactory.getLogger(LoginRequestBodyAdvice.class);
    private static final ApiError INVALID_LOGIN_REQUEST = new ApiError("Invalid login request");

    @Override
    public boolean supports(
            MethodParameter methodParameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return targetType == LoginRequest.class;
    }

    @Override
    public HttpInputMessage beforeBodyRead(
            HttpInputMessage inputMessage,
            MethodParameter parameter,
            Type targetType,
            Class<? extends HttpMessageConverter<?>> converterType)
            throws IOException {
        byte[] body = inputMessage.getBody().readNBytes(MAX_LOGIN_BODY_BYTES + 1);
        if (body.length > MAX_LOGIN_BODY_BYTES) {
            logInvalidRequest();
            throw new LoginRequestTooLargeException();
        }
        ByteArrayInputStream boundedBody = new ByteArrayInputStream(body);
        return new HttpInputMessage() {
            @Override
            public ByteArrayInputStream getBody() {
                return boundedBody;
            }

            @Override
            public org.springframework.http.HttpHeaders getHeaders() {
                return inputMessage.getHeaders();
            }
        };
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableRequest() {
        logInvalidRequest();
        return ResponseEntity.badRequest().body(INVALID_LOGIN_REQUEST);
    }

    @ExceptionHandler(LoginRequestTooLargeException.class)
    ResponseEntity<ApiError> handleOversizedRequest() {
        return ResponseEntity.badRequest().body(INVALID_LOGIN_REQUEST);
    }

    private static void logInvalidRequest() {
        LOGGER.atWarn()
                .addKeyValue("validation.fields", List.of("requestBody"))
                .setMessage("Invalid login request")
                .log();
    }

    private static final class LoginRequestTooLargeException extends RuntimeException {}
}
