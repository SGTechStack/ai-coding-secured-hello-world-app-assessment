package com.example.demo_app.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

class ApiErrorTest {

  private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

  @Test
  void fieldErrorsAreLeftOutWhenEmpty() {
    JsonNode json = objectMapper.valueToTree(ApiError.forStatus(HttpStatus.NOT_FOUND, "/x"));

    assertThat(json.has("fieldErrors")).isFalse();
    assertThat(json.has("status")).isTrue();
  }

  @Test
  void fieldErrorsAreWrittenWhenPresent() {
    ApiError error =
        new ApiError(
            400,
            "VALIDATION_FAILED",
            "Check the highlighted fields",
            Instant.EPOCH,
            "/x",
            List.of(new ApiError.FieldError("username", "Username is required")));

    JsonNode json = objectMapper.valueToTree(error);

    assertThat(json.get("fieldErrors").get(0).get("field").asText()).isEqualTo("username");
    assertThat(json.get("fieldErrors").get(0).get("message").asText())
        .isEqualTo("Username is required");
  }

  @Test
  void unknownAndServerStatusesAreAGeneric500() {
    assertThat(ApiError.forStatus(HttpStatusCode.valueOf(599), "/x"))
        .extracting(ApiError::status, ApiError::code, ApiError::message)
        .containsExactly(500, "INTERNAL_ERROR", "Something went wrong");
    assertThat(ApiError.forStatus(HttpStatus.SERVICE_UNAVAILABLE, "/x").code())
        .isEqualTo("INTERNAL_ERROR");
  }
}
