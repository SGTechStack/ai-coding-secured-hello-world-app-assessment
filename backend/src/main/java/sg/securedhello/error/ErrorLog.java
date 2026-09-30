package sg.securedhello.error;

import org.slf4j.Logger;

import sg.securedhello.logging.EcsLogFormatter;

/**
 * The application's one ERROR line for an exception that reached the envelope as {@link ErrorCode#INTERNAL_ERROR}
 * (LOG §5; T-AUD-002): the throwable's {@code error.type}, {@code error.message} and {@code error.stack_trace}, and the
 * code's {@code error.code} (its status), {@code error.category} and {@code error.follow_up_action}, all in one
 * {@code error} object. The body never carries any of them.
 */
final class ErrorLog {

    private ErrorLog() {
    }

    /** Writes the ERROR line for {@code escaped}, answered as {@link ErrorCode#INTERNAL_ERROR}. */
    static void unhandled(Logger log, String message, Throwable escaped) {
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        log.atError().setCause(escaped)
                .addKeyValue(EcsLogFormatter.ERROR_CODE, Integer.toString(code.status()))
                .addKeyValue(EcsLogFormatter.ERROR_CATEGORY, code.category().value())
                .addKeyValue(EcsLogFormatter.ERROR_FOLLOW_UP_ACTION, code.followUpAction().value())
                .setMessage(message)
                .log();
    }
}
