package sg.example.helloauth.password;

/** The compromised-password check got no answer, so a password can't be judged safe. */
public class CompromisedPasswordCheckUnavailableException extends RuntimeException {

    CompromisedPasswordCheckUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
