package com.eitri.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonWriter;

class SensitiveLogValueMaskerTest {

    @Test
    void masksSensitiveStructuredValuesIncludingContainersButPreservesTheApprovedSessionHash() {
        JsonWriter<Object> writer = JsonWriter.of(members -> {
            members.add("password", "password-value");
            members.add("auth.token", List.of("token-value"));
            members.add("clientSecret", new String[] {"secret-value"});
            members.add("session.id", "session-value");
            members.add("session.hash", "approved-hash");
            new SensitiveLogValueMasker().customize(members);
        });

        String json = writer.writeToString(new Object());

        assertThat(json)
                .doesNotContain("password-value", "token-value", "secret-value", "session-value")
                .contains("approved-hash")
                .contains(SensitiveLogValueMasker.MASK);
    }
}
