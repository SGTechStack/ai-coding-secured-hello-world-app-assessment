package com.example.securedhello.web;

import java.sql.SQLException;

import org.springframework.dao.DataAccessException;

/**
 * The Structured Logging standard's {@code error.category} for an unexpected exception, and whether
 * it needs follow-up action ({@code server}, {@code database} and {@code application} do). Public so
 * code running outside a request (for example the password-reset background executor) can log an
 * unexpected exception the same way {@link GlobalExceptionHandler} does.
 */
public enum ErrorCategory {

	DATABASE("database", true), APPLICATION("application", true);

	private final String value;

	private final boolean followUpAction;

	ErrorCategory(String value, boolean followUpAction) {
		this.value = value;
		this.followUpAction = followUpAction;
	}

	public String value() {
		return value;
	}

	public boolean followUpAction() {
		return followUpAction;
	}

	public static ErrorCategory of(Throwable exception) {
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof DataAccessException || cause instanceof SQLException) {
				return DATABASE;
			}
		}
		return APPLICATION;
	}

}
