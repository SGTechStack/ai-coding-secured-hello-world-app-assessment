package sg.securedhello.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import jakarta.servlet.http.Cookie;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import sg.securedhello.mfa.TotpFactorGrant;
import sg.securedhello.mfa.TotpSecretCipher;
import sg.securedhello.mfa.TotpWindow;
import sg.securedhello.testsupport.Accounts.Account;

import tools.jackson.databind.json.JsonMapper;

/**
 * Enrolled TOTP factors for tests past enrolment: a confirmed {@code totp_user_details} row, sealed under the context's
 * key exactly as enrolment binding leaves it, and verification through the real route.
 */
public final class TotpFactors {

    /** The verification route. */
    public static final String VERIFICATION = "/api/mfa/totp/verification";

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final TotpSecretCipher cipher;
    private final Clock clock;

    public TotpFactors(JdbcTemplate jdbc, TotpSecretCipher cipher, Clock clock) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.clock = clock;
    }

    /** Enrols {@code account} with a fresh secret, never used for a code, and returns the secret. */
    public byte[] enrol(Account account) {
        byte[] secret = new byte[TotpSecretCipher.SECRET_BYTES];
        RANDOM.nextBytes(secret);
        jdbc.update("INSERT INTO totp_user_details (user_id, totp_key, key_version, created_at) VALUES (?, ?, ?, ?)",
                account.id(), cipher.seal(account.id(), secret), cipher.keyVersion(),
                Timestamp.from(clock.instant()));
        return secret;
    }

    /** The code for {@code secret} at the shared clock's current step. */
    public String code(byte[] secret) {
        return code(secret, TotpWindow.counter(clock.instant()));
    }

    /** The code for {@code secret} at {@code counter}. */
    public static String code(byte[] secret, long counter) {
        return TotpWindow.generate(secret, counter, TotpWindow.DIGITS);
    }

    /**
     * A code that fails for {@code secret} at the shared clock's current step: none of the three codes the ±1 window
     * accepts, so a test that counts failures never passes by chance.
     */
    public String wrongCode(byte[] secret) {
        long current = TotpWindow.counter(clock.instant());
        List<String> live = List.of(code(secret, current - 1), code(secret, current), code(secret, current + 1));
        return Stream.of("000000", "000001", "000002", "000003").filter(candidate -> !live.contains(candidate))
                .findFirst().orElseThrow();
    }

    /** Posts {@code code} to the verification route on {@code session}. */
    public static ResultActions verify(MockMvc mockMvc, CsrfSession session, String code) throws Exception {
        return mockMvc.perform(post(VERIFICATION).with(session.inHeader()).contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("code", code))));
    }

    /**
     * Replaces the stored {@code FACTOR_TOTP} of session {@code id} with a plain authority of the same name, as a
     * degraded serialisation round trip could leave it: the name survives, the type and issue time do not (ADR-021).
     */
    public static <S extends Session> void degrade(SessionRepository<S> repository, String id) {
        S session = repository.findById(id);
        SecurityContext context =
                session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        Authentication granted = context.getAuthentication();
        List<GrantedAuthority> degraded = new ArrayList<>(granted.getAuthorities().stream()
                .filter(authority -> !TotpFactorGrant.AUTHORITY.equals(authority.getAuthority())).toList());
        degraded.add(new SimpleGrantedAuthority(TotpFactorGrant.AUTHORITY));
        UsernamePasswordAuthenticationToken plain =
                UsernamePasswordAuthenticationToken.authenticated(granted.getPrincipal(), null, degraded);
        plain.setDetails(granted.getDetails());
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new SecurityContextImpl(plain));
        repository.save(session);
    }

    /** Verifies the current code on {@code session} and returns the rotated session, with its new token. */
    public CsrfSession verified(MockMvc mockMvc, CsrfSession session, byte[] secret) throws Exception {
        MvcResult result = verify(mockMvc, session, code(secret)).andReturn();
        assertThat(result.getResponse().getStatus()).as("verification").isEqualTo(204);
        Cookie rotated = result.getResponse().getCookie("SESSION");
        assertThat(rotated).as("rotated session cookie").isNotNull();
        return SignedIn.refreshed(mockMvc, rotated);
    }
}
