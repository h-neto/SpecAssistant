package pt.haslab.alloyaddons;

public class UncheckedIOException extends RuntimeException {
    public UncheckedIOException() {
    }

    public UncheckedIOException(String message) {
        super(message);
    }

    public UncheckedIOException(String message, Throwable cause) {
        super(message, cause);
    }

    public UncheckedIOException(Throwable cause) {
        super(cause);
    }

    public UncheckedIOException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
