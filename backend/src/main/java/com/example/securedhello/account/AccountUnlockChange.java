package com.example.securedhello.account;

/** Whether an Account was Locked before an admin unlock, for the audit event; it is never Locked after. */
record AccountUnlockChange(boolean before) {
}
