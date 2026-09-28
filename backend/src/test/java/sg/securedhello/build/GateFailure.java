package sg.securedhello.build;

/** A build gate's input cannot be read or does not have the required shape. */
final class GateFailure extends RuntimeException {

    GateFailure(String message) {
        super(message);
    }
}
