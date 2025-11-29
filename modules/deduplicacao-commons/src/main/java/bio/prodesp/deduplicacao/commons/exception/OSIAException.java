package bio.prodesp.deduplicacao.commons.exception;

/**
 * Exception para erros de comunicação com ABIS OSIA
 */
public class OSIAException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int statusCode;
    private final boolean retryable;

    public OSIAException(String message) {
        super(message);
        this.statusCode = 0;
        this.retryable = false;
    }

    public OSIAException(String message, int statusCode, boolean retryable) {
        super(message);
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public OSIAException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.retryable = true; // Por padrão, erros de rede são retryable
    }

    public OSIAException(String message, int statusCode, boolean retryable, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public boolean isClientError() {
        return statusCode >= 400 && statusCode < 500;
    }

    public boolean isServerError() {
        return statusCode >= 500;
    }

    @Override
    public String toString() {
        return String.format("OSIAException[statusCode=%d, retryable=%s, message=%s]",
                statusCode, retryable, getMessage());
    }
}