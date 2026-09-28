package sg.securedhello.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.LogOutputGuard;
import sg.securedhello.testsupport.Proves;
import sg.securedhello.testsupport.TestSecrets;

/**
 * The canary-secret scan over a real context (T-AUD-013): after startup, and after requests that carry the canaries
 * in a header, the query string and a body, no canary has reached stdout, stderr or the audit file in any encoded
 * form. {@link LogOutputGuard} repeats the same scan after every test in the suite.
 */
class CanarySecretScanTest extends CtxDefaultTest {

    @Test
    @Proves("T-AUD-013")
    void noCanaryReachesAnyAppenderAcrossStartupAndRequestsThatCarryThem() throws Exception {
        String basic = Base64.getEncoder().encodeToString((TestSecrets.ADMIN_USERNAME + ":"
                + TestSecrets.ADMIN_PASSWORD).getBytes(StandardCharsets.UTF_8));
        LogOutputGuard.register("server-secret-registered-by-this-test");

        mockMvc.perform(get("/api/hello").queryParam("password", TestSecrets.ADMIN_PASSWORD)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"canary-admin\",\"password\":\"" + TestSecrets.ADMIN_PASSWORD + "\"}"))
                .andExpect(status().is4xxClientError());

        assertThat(LogOutputGuard.capturesStandardStreams()).isTrue();
        assertThat(LogOutputGuard.scanNow(false)).isEmpty();
    }
}
