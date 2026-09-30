package org.eds.demo.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RequestLimitExceededExceptionTest {

  @Test
  void constructor_setsMessageStatusAndRetryAfter() {
    RequestLimitExceededException ex = new RequestLimitExceededException("Rate limit reached", 45L);

    assertThat(ex.getMessage()).isEqualTo("Rate limit reached");
    assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(ex.getRetryAfterSeconds()).isEqualTo(45L);
  }

  @Test
  void builder_setsMessageStatusAndRetryAfter() {
    RequestLimitExceededException ex =
        RequestLimitExceededException.builder()
            .message("Too many emails")
            .retryAfterSeconds(300L)
            .build();

    assertThat(ex.getMessage()).isEqualTo("Too many emails");
    assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(ex.getRetryAfterSeconds()).isEqualTo(300L);
  }
}
