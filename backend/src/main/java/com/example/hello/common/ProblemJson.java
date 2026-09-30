package com.example.hello.common;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Writes a minimal RFC 9457 problem document from places that run before Spring MVC (the
 * security filter chain), keeping the error shape identical to {@link ApiExceptionHandler}.
 */
public final class ProblemJson {

  private ProblemJson() {}

  public static void write(HttpServletResponse response, HttpStatus status, String detail)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
    StringBuilder json =
        new StringBuilder()
            .append("{\"type\":\"about:blank\",\"title\":\"")
            .append(escape(status.getReasonPhrase()))
            .append("\",\"status\":")
            .append(status.value())
            .append(",\"detail\":\"")
            .append(escape(detail))
            .append('"');
    if (correlationId != null) {
      json.append(",\"correlationId\":\"").append(escape(correlationId)).append('"');
    }
    json.append('}');
    response.getWriter().write(json.toString());
    response.getWriter().flush();
  }

  static String escape(String value) {
    StringBuilder out = new StringBuilder(value.length());
    for (char c : value.toCharArray()) {
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    return out.toString();
  }
}
