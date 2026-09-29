package com.eitri.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.sql.SQLException;
import java.text.ParseException;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.http.HttpStatus;

class ErrorCategoryResolverTest {

    @Test
    void resolvesEveryStandardCategoryAndFallback() {
        assertThat(ErrorCategoryResolver.resolve(
                        HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "", null, null, null)))
                .isEqualTo("server");
        assertThat(ErrorCategoryResolver.resolve(new ConnectException())).isEqualTo("network");
        assertThat(ErrorCategoryResolver.resolve(new AccessDeniedException("denied"))).isEqualTo("cert/auth");
        assertThat(ErrorCategoryResolver.resolve(new SQLException())).isEqualTo("database");
        assertThat(ErrorCategoryResolver.resolve(new ParseException("bad", 0))).isEqualTo("data");
        assertThat(ErrorCategoryResolver.resolve(new IllegalStateException())).isEqualTo("application");
        assertThat(ErrorCategoryResolver.resolve(new Exception())).isEqualTo("others");
        assertThat(ErrorCategoryResolver.resolve(null)).isEqualTo("others");
    }
}
