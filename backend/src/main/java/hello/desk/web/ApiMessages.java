package hello.desk.web;

public final class ApiMessages {

    public static final String INVALID_CREDENTIALS = "Invalid username or password";
    public static final String TOO_MANY_ATTEMPTS = "Too many login attempts from this network. Try again later.";
    public static final String RESET_REQUEST =
            "If an account exists for that email, password reset instructions have been sent.";
    public static final String RESET_INVALID = "This reset link is invalid or has expired.";
    public static final String RESET_OK = "Your password has been updated. Log in with the new password.";
    public static final String SELF_ACTION = "You cannot change your own account.";
    public static final String USER_NOT_FOUND = "No account matches that id.";
    public static final String USERNAME_TAKEN = "That username is already registered.";
    public static final String EMAIL_TAKEN = "That email is already registered.";
    public static final String WEAK_PASSWORD = "Password must be at least 12 characters.";
    public static final String INVALID_REQUEST = "Request body is invalid.";
    public static final String UNAUTHORIZED = "Unauthorized";
    public static final String FORBIDDEN = "Forbidden";

    private ApiMessages() {
    }
}
