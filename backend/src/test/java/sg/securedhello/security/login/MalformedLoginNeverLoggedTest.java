package sg.securedhello.security.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;

import sg.securedhello.error.ErrorCode;
import sg.securedhello.testsupport.CsrfSession;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.LogOutputGuard;
import sg.securedhello.testsupport.Proves;

/**
 * A credential-bearing login body the parser rejects never puts the submitted password in any appender (Std §3.4;
 * R-AUD-008): the refusal is the 400 envelope, and the parse error itself is not logged. The password is registered
 * with the suite's canary scan too, which reads stdout, stderr and the audit file.
 */
@ExtendWith(OutputCaptureExtension.class)
class MalformedLoginNeverLoggedTest extends CtxDefaultTest {

    private static final String PASSWORD = "malformed-login-canary-73e2";

    @Test
    @Proves("T-AUD-018")
    void aMalformedBodyCarryingAPasswordLeavesItInNoAppender(CapturedOutput output) throws Exception {
        LogOutputGuard.register(PASSWORD);
        int mark = output.getAll().length();

        for (String body : List.of(
                "{\"username\":\"someone\",\"password\":\"" + PASSWORD + "\"",
                "{\"username\":\"someone\",\"password\":[\"" + PASSWORD + "\"]}",
                "{\"username\":{\"x\":1},\"password\":\"" + PASSWORD + "\"}",
                "{\"password\":\"" + PASSWORD + "\",\"username\":\"someone\",}")) {
            mockMvc.perform(post("/api/login").with(CsrfSession.bootstrap(mockMvc).inHeader())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(problem(ErrorCode.VALIDATION_FAILED));
        }

        assertThat(output.getAll().substring(mark)).doesNotContain(PASSWORD);
    }
}
