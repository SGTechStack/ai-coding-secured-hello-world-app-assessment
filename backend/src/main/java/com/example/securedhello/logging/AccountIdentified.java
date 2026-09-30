package com.example.securedhello.logging;

import java.util.UUID;

/**
 * An authenticated principal that knows its Account's UUID. Logs identify Accounts only by this
 * UUID, never by username or email.
 */
public interface AccountIdentified {

	UUID accountId();

}
