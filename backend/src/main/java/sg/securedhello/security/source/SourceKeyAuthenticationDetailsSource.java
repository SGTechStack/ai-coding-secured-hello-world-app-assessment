package sg.securedhello.security.source;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.authentication.AuthenticationDetailsSource;

/**
 * Builds the sign-in's {@link SourceKeyAuthenticationDetails}. The early rate-limit filter has already derived the
 * request's source key and left it in {@link SourceKeyResolver#REQUEST_ATTRIBUTE}; that key is used as it is, so a
 * request's source key is derived once (T-RL-029). Only a request that bypassed the filter falls back to the resolver.
 */
public final class SourceKeyAuthenticationDetailsSource
        implements AuthenticationDetailsSource<HttpServletRequest, SourceKeyAuthenticationDetails> {

    private final SourceKeyResolver resolver;

    public SourceKeyAuthenticationDetailsSource(SourceKeyResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public SourceKeyAuthenticationDetails buildDetails(HttpServletRequest request) {
        return new SourceKeyAuthenticationDetails(
                request.getAttribute(SourceKeyResolver.REQUEST_ATTRIBUTE) instanceof SourceKey derived
                        ? derived
                        : resolver.resolve(request));
    }
}
