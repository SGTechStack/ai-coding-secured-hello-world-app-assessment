package sg.securedhello.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import sg.securedhello.testsupport.Proves;

import tools.jackson.databind.json.JsonMapper;

/** The generated schema accepts exactly the envelope, and the enum keeps its pairings closed (ADR-031). */
class ErrorContractSchemaTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static Map<String, Object> envelope(ErrorCode code) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", code.type());
        body.put("title", code.title());
        body.put("status", code.status());
        body.put("detail", code.detail());
        body.put("instance", "/api/hello");
        body.put("traceId", "0123456789abcdef0123456789abcdef");
        body.put("code", code.name());
        ErrorContract.EXTENSIONS.getOrDefault(code, Map.of())
                .forEach((name, extension) -> body.put(name, extension.values().getFirst()));
        return body;
    }

    private static java.util.List<String> violations(Map<String, Object> body) {
        return ErrorContract.violations(JSON.writeValueAsString(body));
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    void everyCodesEnvelopeConforms(ErrorCode code) {
        assertThat(violations(envelope(code))).isEmpty();
    }

    @Test
    void anExtensionMemberIsRequiredOnItsCodeAndRefusedOnAnyOther() {
        Map<String, Object> withoutRule = envelope(ErrorCode.PASSWORD_REJECTED);
        withoutRule.remove("rule");
        assertThat(violations(withoutRule)).isNotEmpty();

        Map<String, Object> unknownRule = envelope(ErrorCode.PASSWORD_REJECTED);
        unknownRule.put("rule", "NOT_A_RULE");
        assertThat(violations(unknownRule)).isNotEmpty();

        Map<String, Object> foreign = envelope(ErrorCode.VALIDATION_FAILED);
        foreign.put("rule", "TOO_WEAK");
        assertThat(violations(foreign)).isNotEmpty();
    }

    @Test
    @Proves("T-AUTH-011")
    void aBasicErrorControllerBodyIsRejected() {
        Map<String, Object> boot = new LinkedHashMap<>();
        boot.put("timestamp", "2026-09-29T00:00:00.000+00:00");
        boot.put("status", 500);
        boot.put("error", "Internal Server Error");
        boot.put("path", "/api/hello");
        assertThat(violations(boot)).isNotEmpty();
    }

    @Test
    @Proves("T-AUTH-011")
    void anUnknownCodeIsRejected() {
        Map<String, Object> body = envelope(ErrorCode.ACCESS_DENIED);
        body.put("code", "NOT_A_CODE");
        assertThat(violations(body)).isNotEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {"AUTHENTICATION_FAILED", "INTERNAL_ERROR"})
    @Proves("T-AUTH-011")
    void aMissingMemberIsRejected(ErrorCode code) {
        for (String member : envelope(code).keySet()) {
            Map<String, Object> body = envelope(code);
            body.remove(member);
            assertThat(violations(body)).as("without %s", member).isNotEmpty();
        }
    }

    @Test
    @Proves("T-AUTH-011")
    void aCodeWithAnotherStatusOrDetailIsRejected() {
        Map<String, Object> wrongStatus = envelope(ErrorCode.ACCESS_DENIED);
        wrongStatus.put("status", 401);
        assertThat(violations(wrongStatus)).isNotEmpty();

        Map<String, Object> exceptionMessage = envelope(ErrorCode.INTERNAL_ERROR);
        exceptionMessage.put("detail", "NullPointerException at line 42");
        assertThat(violations(exceptionMessage)).isNotEmpty();
    }

    @Test
    void anUndeclaredMemberIsRejected() {
        Map<String, Object> body = envelope(ErrorCode.ACCESS_DENIED);
        body.put("message", "extra");
        assertThat(violations(body)).isNotEmpty();
    }

    @Test
    void codesTitlesAndTypesAreUnique() {
        assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::type)).doesNotHaveDuplicates();
        assertThat(Arrays.stream(ErrorCode.values()).map(ErrorCode::title)).doesNotHaveDuplicates();
        assertThat(ErrorCode.ACCESS_DENIED.type()).isEqualTo(ErrorCode.TYPE_PREFIX + "access-denied");
    }

    @Test
    void statusFallbacksFollowTheContract() {
        assertThat(ErrorCode.forStatus(400)).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(ErrorCode.forStatus(406)).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(ErrorCode.forStatus(413)).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(ErrorCode.forStatus(415)).isEqualTo(ErrorCode.VALIDATION_FAILED);
        assertThat(ErrorCode.forStatus(401)).isEqualTo(ErrorCode.AUTHENTICATION_FAILED);
        assertThat(ErrorCode.forStatus(403)).isEqualTo(ErrorCode.ACCESS_DENIED);
        assertThat(ErrorCode.forStatus(404)).isEqualTo(ErrorCode.ACCESS_DENIED);
        assertThat(ErrorCode.forStatus(405)).isEqualTo(ErrorCode.ACCESS_DENIED);
        assertThat(ErrorCode.forStatus(429)).isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
        assertThat(ErrorCode.forStatus(500)).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(ErrorCode.forStatus(418)).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }
}
