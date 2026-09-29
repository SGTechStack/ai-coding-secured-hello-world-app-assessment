package com.example.securedhello.web;

import java.sql.SQLException;

import org.springframework.dao.DataAccessException;

/**
 * The Structured Logging standard's {@code error.category} for an unexpected exception, and whether
 * it needs follow-up action ({@code server}, {@code database} and {@code application} do).
 */
enum ErrorCategory {

	DATABASE("database", true), APPLICATION("application", true);

	private final String value;

	private final boolean followUpAction;

	ErrorCategory(String value, boolean followUpAction) {
		this.value = value;
		this.followUpAction = followUpAction;
	}

	String value() {
		return value;
	}

	boolean followUpAction() {
		return followUpAction;
	}

	static ErrorCategory of(Throwable exception) {
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof DataAccessException || cause instanceof SQLException) {
				return DATABASE;
			}
		}
		return APPLICATION;
	}

}
