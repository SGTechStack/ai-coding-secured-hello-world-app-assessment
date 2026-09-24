package com.sgtechstack.helloworldauthapp.logging;

import org.slf4j.MDC;

/**
 * Read access to the per-request diagnostic context populated by
 * {@link LoggingContextFilter}.
 *
 * <p>This exists so that code with no {@code HttpServletRequest} in scope can
 * still record which request it was serving. The admin audit trail is the
 * motivating case: {@code AdminUserManagementService} is a transactional
 * service that deliberately knows nothing about HTTP, but an audit row for an
 * irreversible action is much less useful without the caller's address and a
 * correlation id linking it to the surrounding log lines.
 *
 * <p>Passing both values down through every service signature was the
 * alternative. It was rejected because it would put transport-level details
 * into the domain layer's method contracts for the benefit of one cross-cutting
 * concern — and the servlet container already maintains exactly the
 * thread-bound scope needed, which is what MDC is for.
 *
 * <p>Values are absent, not wrong, outside a request (a scheduled purge, a
 * startup runner, a unit test). Every accessor therefore answers with an
 * explicit marker rather than null, so a caller cannot accidentally write an
 * empty column and leave a reader guessing whether the field was missing or the
 * value was.
 */
public final class LoggingContext {

    /** MDC key for the per-request correlation id. */
    public static final String REQUEST_ID = "requestId";

    /** MDC key for the resolved client address. */
    public static final String CLIENT_IP = "clientIp";

    static final String ABSENT = "none";

    private LoggingContext() {
    }

    /**
     * The correlation id of the request being served, or {@code none} when
     * not serving one.
     */
    public static String requestId() {
        return valueOr(REQUEST_ID);
    }

    /**
     * The resolved client address of the request being served, or {@code none}
     * when not serving one.
     *
     * <p>"Resolved" means via {@code ClientIpResolver}, so this honours the
     * declared proxy-hop count rather than blindly trusting
     * {@code X-Forwarded-For} or blindly ignoring it.
     */
    public static String clientIp() {
        return valueOr(CLIENT_IP);
    }

    private static String valueOr(String key) {
        String value = MDC.get(key);
        return value == null || value.isBlank() ? ABSENT : value;
    }
}
