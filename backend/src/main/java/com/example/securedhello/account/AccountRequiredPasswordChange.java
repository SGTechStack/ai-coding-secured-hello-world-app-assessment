package com.example.securedhello.account;

/**
 * Whether an Account already had to change its password before an Admin required it, for the audit
 * event; it always has to afterwards.
 */
record AccountRequiredPasswordChange(boolean before) {
}
