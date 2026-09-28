package sg.securedhello.credential;

/** The kinds of credential token; the database admits exactly these ({@code ck_credential_tokens_type}, ADR-007). */
public enum CredentialTokenType {
    ACTIVATION,
    PASSWORD_RESET
}
