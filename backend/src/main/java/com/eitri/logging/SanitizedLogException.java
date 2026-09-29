package com.eitri.logging;

/** Throwable safe for structured logs: it retains code locations but never the original message or cause. */
public final class SanitizedLogException extends RuntimeException {

    private SanitizedLogException(String safeMessage, StackTraceElement[] stackTrace) {
        super(safeMessage);
        setStackTrace(stackTrace.clone());
    }

    public static SanitizedLogException from(Throwable failure, String safeMessage) {
        return new SanitizedLogException(safeMessage, failure.getStackTrace());
    }
}
