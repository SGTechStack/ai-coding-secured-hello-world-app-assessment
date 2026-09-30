package com.example.securedhello.logging;

import java.util.Map;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Copies the submitting thread's MDC (trace ID, correlation ID) into a background task, so its log
 * lines still correlate with the request that queued it, then clears the executor thread's MDC
 * afterwards so it never leaks into the next task run on the same pooled thread.
 */
public final class MdcCopyingTaskDecorator implements TaskDecorator {

	@Override
	public Runnable decorate(Runnable runnable) {
		Map<String, String> context = MDC.getCopyOfContextMap();
		return () -> {
			Map<String, String> previous = MDC.getCopyOfContextMap();
			setContext(context);
			try {
				runnable.run();
			}
			finally {
				setContext(previous);
			}
		};
	}

	private static void setContext(Map<String, String> context) {
		if (context != null) {
			MDC.setContextMap(context);
		}
		else {
			MDC.clear();
		}
	}

}
