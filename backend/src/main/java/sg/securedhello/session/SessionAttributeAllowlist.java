package sg.securedhello.session;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;

import org.jspecify.annotations.Nullable;
import org.springframework.core.ConfigurableObjectInputStream;
import org.springframework.core.serializer.Deserializer;

/**
 * Deserialises a stored session attribute through a class allowlist (R-SES-003; JEP 290). Spring Session JDBC keeps
 * attributes JDK-serialised and installs no filter of its own, so this is the only barrier between write access to
 * the session tables and a deserialisation gadget.
 *
 * <p>The allowlist is the types the application stores: the security context (a {@code SecurityContextImpl} holding
 * an authentication token, its {@code SignedInUser} principal and authorities, {@code FactorGrantedAuthority}
 * included), the {@code AUTH_INSTANT}, the CSRF token and Spring Session's concurrent-session expiry flag, plus the
 * JDK types they are built from. Storing a new type in a session means adding it here.
 */
final class SessionAttributeAllowlist implements Deserializer<Object> {

    static final String PATTERN = String.join(";",
            "maxdepth=20", "maxrefs=1000", "maxarray=1024",
            "java.lang.Object", "java.lang.String", "java.lang.Number", "java.lang.Long", "java.lang.Integer",
            "java.lang.Boolean", "java.lang.Enum",
            "java.util.*", "java.time.Ser", "java.time.Instant",
            "org.springframework.security.core.**",
            "org.springframework.security.authentication.*",
            "org.springframework.security.web.authentication.WebAuthenticationDetails",
            "org.springframework.security.web.csrf.DefaultCsrfToken",
            "sg.securedhello.user.SignedInUser",
            "!*");

    private static final ObjectInputFilter FILTER = ObjectInputFilter.Config.createFilter(PATTERN);

    private final @Nullable ClassLoader classLoader;

    SessionAttributeAllowlist(@Nullable ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public Object deserialize(InputStream inputStream) throws IOException {
        ObjectInputStream objects = new ConfigurableObjectInputStream(inputStream, classLoader);
        objects.setObjectInputFilter(FILTER);
        try {
            return objects.readObject();
        } catch (ClassNotFoundException ex) {
            throw new IOException("Failed to deserialise a session attribute", ex);
        }
    }
}
