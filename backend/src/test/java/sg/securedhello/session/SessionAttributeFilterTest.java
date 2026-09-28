package sg.securedhello.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.ConversionService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.util.ReflectionTestUtils;

import sg.securedhello.testsupport.CtxDefaultTest;
import sg.securedhello.testsupport.Proves;

/**
 * Session attributes are JDK-deserialised from the store, so an allowlist decides which classes may come back
 * (R-SES-003). The session is written and read through the real JDBC repository.
 */
class SessionAttributeFilterTest extends CtxDefaultTest {

    private static final String CSRF_ATTRIBUTE =
            "org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN";

    /** Serializable, but not a type the application ever stores in a session. */
    record NotAllowlisted(String value) implements Serializable {
    }

    @Autowired
    private SessionRepository<? extends Session> repository;

    @Autowired
    private ConversionService springSessionConversionService;

    private Session reload(String attribute, Object value) {
        return reload(repository, attribute, value);
    }

    /** Writes one attribute through the real repository and reads the session back from the store. */
    private static <S extends Session> S reload(SessionRepository<S> repository, String attribute, Object value) {
        S session = repository.createSession();
        session.setAttribute(attribute, value);
        repository.save(session);
        return repository.findById(session.getId());
    }

    @Test
    @Proves("T-SES-025")
    void theAllowlistIsTheOnlyFilterOnTheDeserialisationPath() {
        // First-implementation record: no JVM-wide filter exists, so the per-stream allowlist is the only barrier.
        assertThat(ObjectInputFilter.Config.getSerialFilter()).isNull();
        assertThat(ReflectionTestUtils.getField(repository, "conversionService"))
                .isSameAs(springSessionConversionService);
    }

    @Test
    @Proves("T-SES-025")
    void theStoredTypesRoundTrip() {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"),
                FactorGrantedAuthority.withAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY).issuedAt(now).build());
        User user = new User("alice", "", authorities);
        SecurityContextImpl context = new SecurityContextImpl(
                UsernamePasswordAuthenticationToken.authenticated(user, null, authorities));
        DefaultCsrfToken token = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "token-value");

        String contextKey = HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
        assertThat((Object) reload(contextKey, context).getAttribute(contextKey)).isEqualTo(context);
        assertThat((Object) reload(SessionAttributes.AUTH_INSTANT, now).getAttribute(SessionAttributes.AUTH_INSTANT))
                .isEqualTo(now);
        DefaultCsrfToken reloaded = reload(CSRF_ATTRIBUTE, token).getAttribute(CSRF_ATTRIBUTE);
        assertThat(reloaded.getToken()).isEqualTo(token.getToken());
    }

    @Test
    @Proves("T-SES-025")
    void aClassOffTheAllowlistIsRefused() {
        Session session = reload("gadget", new NotAllowlisted("x"));

        assertThatThrownBy(() -> session.getAttribute("gadget"))
                .hasRootCauseInstanceOf(InvalidClassException.class)
                .rootCause().hasMessageContaining("REJECTED");
    }
}
