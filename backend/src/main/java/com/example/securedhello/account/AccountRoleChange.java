package com.example.securedhello.account;

/** An Account's role before and after an admin change, for the audit event. */
record AccountRoleChange(Role before, Role after) {
}
