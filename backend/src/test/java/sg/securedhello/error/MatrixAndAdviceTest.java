package sg.securedhello.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static sg.securedhello.testsupport.CsrfSession.validToken;
import static sg.securedhello.testsupport.ProblemAssertions.problem;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import sg.securedhello.mfa.TotpFactorGrant;
import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;

/**
 * The matrix order and the controller-advice producer, which need routes the application does not have yet.
 *
 * <p>This class deliberately starts its own context: it adds matrix rows (an {@code /api/admin/**} guard, as ADR-043
 * prescribes, and a USER probe route) and imports {@link ProbeController}. That is the only way to show that the
 * role-definition {@code denyAll()} wins over an admin guard that would otherwise admit the request, and that an
 * exception raised in a controller is rendered as the envelope.
 */
@Import(MatrixAndAdviceTest.ProbeController.class)
@TestPropertySource(properties = {
        "app.security.authorization.roles.ADMIN[0].method=GET",
        "app.security.authorization.roles.ADMIN[0].path=/api/admin/**",
        "app.security.authorization.roles.ADMIN[1].method=PUT",
        "app.security.authorization.roles.ADMIN[1].path=/api/admin/**",
        "app.security.authorization.roles.USER[0].method=GET",
        "app.security.authorization.roles.USER[0].path=/api/probe/**",
        "app.security.authorization.roles.USER[1].method=POST",
        "app.security.authorization.roles.USER[1].path=/api/probe/**"})
class MatrixAndAdviceTest extends CtxDefaultTest {

    private static final String EXCEPTION_MESSAGE = "controller-internal-detail-91c2";

    @RestController
    static class ProbeController {

        @GetMapping({"/api/admin/probe", "/api/admin/roles/probe"})
        String adminProbe() {
            return "reached";
        }

        @GetMapping("/api/probe/ok")
        String ok() {
            return "reached";
        }

        @GetMapping("/api/probe/boom")
        String boom() {
            throw new IllegalStateException(EXCEPTION_MESSAGE);
        }

        @GetMapping("/api/probe/denied")
        String denied() {
            throw new AccessDeniedException(EXCEPTION_MESSAGE);
        }

        @PostMapping("/api/probe/echo")
        Map<String, String> echo(@RequestBody Map<String, String> body) {
            return body;
        }
    }

    /** An administrator holding both factors, issued now, so the admin guard's factor rule passes (ADR-026). */
    private RequestPostProcessor verifiedAdmin() {
        return user("admin-guard").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"),
                FactorGrantedAuthority.withAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY)
                        .issuedAt(clock.instant()).build(),
                FactorGrantedAuthority.withAuthority(TotpFactorGrant.AUTHORITY).issuedAt(clock.instant()).build());
    }

    @Test
    @Proves("T-ADM-020")
    void theAdminGuardAdmitsAnAdminButTheRoleDefinitionDenyWinsOverIt() throws Exception {
        mockMvc.perform(get("/api/admin/probe").with(verifiedAdmin()))
                .andExpect(status().isOk())
                .andExpect(content().string("reached"));

        mockMvc.perform(get("/api/admin/roles/probe").with(user("admin-guard").roles("ADMIN")))
                .andExpect(problem(ErrorCode.ACCESS_DENIED));
        mockMvc.perform(put("/api/admin/roles/probe").with(validToken(mockMvc))
                        .with(user("admin-guard").roles("ADMIN")))
                .andExpect(problem(ErrorCode.ACCESS_DENIED));
    }

    @Test
    void aRoleGuardAdmitsOnlyItsRole() throws Exception {
        mockMvc.perform(get("/api/probe/ok").with(user("user-probe").roles("USER"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/probe/ok").with(user("admin-probe").roles("ADMIN")))
                .andExpect(problem(ErrorCode.ACCESS_DENIED));
        mockMvc.perform(get("/api/admin/probe").with(user("user-probe").roles("USER")))
                .andExpect(problem(ErrorCode.ACCESS_DENIED));
    }

    @Test
    @Proves({"T-AUTH-011", "T-AUTH-008"})
    void aControllerExceptionIsAnInternalErrorWithoutItsMessage() throws Exception {
        String body = mockMvc.perform(get("/api/probe/boom").with(user("user-boom").roles("USER"))
                        .accept(MediaType.TEXT_HTML))
                .andExpect(problem(ErrorCode.INTERNAL_ERROR))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).doesNotContain(EXCEPTION_MESSAGE).contains("\"instance\":\"/api/probe/boom\"");
    }

    @Test
    @Proves("T-AUTH-011")
    void anAccessDeniedExceptionFromAControllerGoesBackToTheSecurityHandler() throws Exception {
        mockMvc.perform(get("/api/probe/denied").with(user("user-denied").roles("USER")))
                .andExpect(problem(ErrorCode.ACCESS_DENIED));
    }

    @Test
    @Proves({"T-AUTH-011", "T-AUTH-008"})
    void anUnreadableBodyIsAValidationFailure() throws Exception {
        mockMvc.perform(post("/api/probe/echo").with(validToken(mockMvc)).with(user("user-echo").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(problem(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    @Proves("T-AUTH-011")
    void anUnsupportedMediaTypeIsAValidationFailure() throws Exception {
        mockMvc.perform(post("/api/probe/echo").with(validToken(mockMvc)).with(user("user-echo").roles("USER"))
                        .contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(problem(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    @Proves("T-AUTH-011")
    void aPermittedPathWithNoHandlerIsAccessDeniedNotAContainer404() throws Exception {
        mockMvc.perform(get("/api/probe/nothing-here").with(user("user-404").roles("USER")))
                .andExpect(problem(ErrorCode.ACCESS_DENIED));
    }

    @Test
    @Proves("T-AUTH-011")
    void aPermittedPathWithTheWrongMethodIsAccessDeniedNotA405() throws Exception {
        mockMvc.perform(post("/api/probe/ok").with(validToken(mockMvc)).with(user("user-405").roles("USER")))
                .andExpect(problem(ErrorCode.ACCESS_DENIED));
    }
}
