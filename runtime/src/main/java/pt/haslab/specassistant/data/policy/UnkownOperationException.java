package pt.haslab.specassistant.data.policy;

public class UnkownOperationException extends RuntimeException{

    public UnkownOperationException() {
    }

    public UnkownOperationException(String message) {
        super(message);
    }

    public UnkownOperationException(String message, Throwable cause) {
        super(message, cause);
    }

    public UnkownOperationException(Throwable cause) {
        super(cause);
    }

    public UnkownOperationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
