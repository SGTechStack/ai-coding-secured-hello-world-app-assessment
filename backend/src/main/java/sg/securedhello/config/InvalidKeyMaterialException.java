package sg.securedhello.config;

/** A configured key failed its shape check. The message names the property and never carries its value. */
public class InvalidKeyMaterialException extends IllegalStateException {

    public InvalidKeyMaterialException(String property, String reason) {
        super("Startup refused: " + property + " " + reason);
    }
}
