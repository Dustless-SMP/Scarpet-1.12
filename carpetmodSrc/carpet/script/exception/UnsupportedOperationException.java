package carpet.script.exception;

public class UnsupportedOperationException extends InternalExpressionException{
    public UnsupportedOperationException(String message) {
        super("Unsupported expression: "+message);
    }
}
