package com.example.securedhello.observability;

/**
 * What the SPA can report about itself (story 111). The kind is the whole description of the event:
 * there is no room for a message, a stack trace or anything the person using the SPA typed, so a
 * report cannot carry user data or error details however the SPA is changed.
 */
enum ClientEventKind {

	/** A render error the SPA's {@code ErrorBoundary} caught. */
	RENDER_ERROR(false),

	/** An error that reached the browser window unhandled. */
	UNCAUGHT_ERROR(false),

	/** A rejected promise nothing handled. */
	UNHANDLED_REJECTION(false),

	/** Time from starting to navigate to the page until its first response byte. */
	TIME_TO_FIRST_BYTE(true),

	/** Time from starting to navigate to the page until its load event finished. */
	PAGE_LOAD(true);

	private final boolean timed;

	ClientEventKind(boolean timed) {
		this.timed = timed;
	}

	/** Whether this kind measures a duration (performance) rather than counting a failure (error). */
	boolean isTimed() {
		return this.timed;
	}

}
