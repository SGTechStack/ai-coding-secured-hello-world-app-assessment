package sg.securedhello.recovery;

/**
 * The runner refuses and changes nothing. The message is for the operator and names no secret: it is printed to
 * stderr as it is.
 */
class RecoveryRefusedException extends RuntimeException {

    RecoveryRefusedException(String message) {
        super(message);
    }
}
