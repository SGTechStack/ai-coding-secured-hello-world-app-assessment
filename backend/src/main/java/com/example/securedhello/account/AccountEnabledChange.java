package com.example.securedhello.account;

/** An Account's enabled state before and after an admin change, for the audit event. */
record AccountEnabledChange(boolean before, boolean after) {
}
