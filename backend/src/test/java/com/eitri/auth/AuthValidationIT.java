package com.eitri.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import com.eitri.testsupport.SessionClient;
import com.eitri.testsupport.SessionClient.Session;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AuthValidationIT {

    private static final String INVALID_RESPONSE =
            """
            {"message":"Invalid login request"}
            """;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private LoginIpThrottle ipThrottle;

    @Test
    void invalidFieldsReturnTheGenericValidationResponseWithoutAuthenticating() throws Exception {
        Session csrf = fetchSession();

        for (String body : invalidBodies().toList()) {
            assertThat(login(csrf, body))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .isStrictlyEqualTo(INVALID_RESPONSE);
        }

        verifyNoInteractions(authenticationManager, ipThrottle);
    }

    @Test
    void malformedAndEmptyBodiesUseTheGenericValidationResponseWithoutAuthenticating() throws Exception {
        Session csrf = fetchSession();

        for (String body : List.of("{\"username\":\"malformed-canary\"", "")) {
            assertThat(login(csrf, body))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .isStrictlyEqualTo(INVALID_RESPONSE);
        }

        verifyNoInteractions(authenticationManager, ipThrottle);
    }

    @Test
    void malformedBodyWarningContainsOnlyTheRequestBodyFieldName(CapturedOutput output) throws Exception {
        Session csrf = fetchSession();
        String bodyCanary = "malformed-body-value-canary";

        assertThat(login(csrf, "{\"username\":\"" + bodyCanary + "\""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .isStrictlyEqualTo(INVALID_RESPONSE);

        String event = logLine(output, LoginRequestBodyAdvice.class, "Invalid login request");
        assertThat(JsonPath.<String>read(event, "$.log.level")).isEqualTo("WARN");
        assertThat(JsonPath.<List<String>>read(event, "$.validation.fields"))
                .containsExactly("requestBody");
        assertThat(event).doesNotContain(bodyCanary);
        verifyNoInteractions(authenticationManager, ipThrottle);
    }

    @Test
    void validationWarningContainsOnlyStableFieldNamesAndNoSubmittedValues(CapturedOutput output)
            throws Exception {
        Session csrf = fetchSession();
        String usernameCanary = "submitted-username-canary!";
        String passwordCanary = "submitted-password-canary-" + "x".repeat(129);

        assertThat(login(
                        csrf,
                        "{\"username\":\"" + usernameCanary + "\",\"password\":\""
                                + passwordCanary + "\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST);

        String event = logLine(output, AuthController.class, "Invalid login request");
        assertThat(JsonPath.<String>read(event, "$.log.level")).isEqualTo("WARN");
        assertThat(JsonPath.<List<String>>read(event, "$.validation.fields"))
                .containsExactly("username", "password");
        assertThat(event).doesNotContain(usernameCanary, passwordCanary);
        verifyNoInteractions(authenticationManager, ipThrottle);
    }

    @Test
    void requestBodiesOverFourKilobytesAreRejectedAndLoggedWithoutBeingDeserialized(
            CapturedOutput output) throws Exception {
        Session csrf = fetchSession();
        String bodyCanary = "oversized-body-value-canary";
        String oversizedBody = "{\"username\":\"johndoe\",\"password\":\"Password123!\",\"padding\":\""
                + bodyCanary + "x".repeat(4096) + "\"}";

        assertThat(login(csrf, oversizedBody))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .isStrictlyEqualTo(INVALID_RESPONSE);

        String event = logLine(output, LoginRequestBodyAdvice.class, "Invalid login request");
        assertThat(JsonPath.<String>read(event, "$.log.level")).isEqualTo("WARN");
        assertThat(JsonPath.<List<String>>read(event, "$.validation.fields"))
                .containsExactly("requestBody");
        assertThat(event).doesNotContain(bodyCanary);
        verifyNoInteractions(authenticationManager, ipThrottle);
    }

    private static Stream<String> invalidBodies() {
        return Stream.of(
                "{}",
                "{\"username\":\"   \",\"password\":\"Password123!\"}",
                "{\"username\":\"bad!name\",\"password\":\"Password123!\"}",
                "{\"username\":\"" + "a".repeat(65) + "\",\"password\":\"Password123!\"}",
                "{\"username\":\"johndoe\"}",
                "{\"username\":\"johndoe\",\"password\":\" \\t \"}",
                "{\"username\":\"johndoe\",\"password\":\"" + "x".repeat(129) + "\"}");
    }

    private Session fetchSession() throws Exception {
        return SessionClient.fetchCsrf(mvc);
    }

    private MvcTestResult login(Session session, String body) {
        return session.login(mvc, body);
    }

    private static String logLine(CapturedOutput output, Class<?> logger, String message) {
        String loggerName = LoggerFactory.getLogger(logger).getName();
        return output.getAll().lines()
                .filter(line -> line.contains("\"logger\":\"" + loggerName + "\""))
                .filter(line -> line.contains("\"message\":\"" + message + "\""))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No structured validation warning found"));
    }
}
