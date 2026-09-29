package com.eitri.logging;

import jakarta.persistence.PersistenceException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.net.ssl.SSLException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;

/** Maps failures to the standard operational error categories. */
public final class ErrorCategoryResolver {

    private static final List<Map.Entry<Predicate<Throwable>, String>> RULES = List.of(
            Map.entry(
                    failure -> failure instanceof HttpServerErrorException
                            || (failure instanceof ResponseStatusException response
                                    && response.getStatusCode().is5xxServerError()),
                    "server"),
            Map.entry(
                    failure -> failure instanceof ConnectException
                            || failure instanceof SocketTimeoutException
                            || failure instanceof UnknownHostException
                            || failure instanceof SocketException,
                    "network"),
            Map.entry(
                    failure -> failure instanceof SSLException
                            || failure instanceof CertificateException
                            || failure instanceof AccessDeniedException
                            || failure instanceof AuthenticationException
                            || (failure instanceof ResponseStatusException response
                                    && (response.getStatusCode().value() == 401
                                            || response.getStatusCode().value() == 403)),
                    "cert/auth"),
            Map.entry(
                    failure -> failure instanceof SQLException
                            || failure instanceof DataAccessException
                            || failure instanceof PersistenceException,
                    "database"),
            Map.entry(
                    failure -> failure instanceof MethodArgumentNotValidException
                            || failure instanceof JacksonException
                            || failure instanceof ParseException,
                    "data"),
            Map.entry(
                    failure -> failure instanceof IllegalArgumentException
                            || failure instanceof IllegalStateException
                            || failure instanceof UnsupportedOperationException,
                    "application"));

    private ErrorCategoryResolver() {}

    public static String resolve(Throwable failure) {
        if (failure == null) {
            return "others";
        }
        return RULES.stream()
                .filter(rule -> rule.getKey().test(failure))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("others");
    }
}
