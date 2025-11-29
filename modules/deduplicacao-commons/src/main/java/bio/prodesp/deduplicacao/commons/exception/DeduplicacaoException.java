package bio.prodesp.deduplicacao.commons.exception;

/**
 * Exception base para erros de deduplicação
 */
public class DeduplicacaoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DeduplicacaoException(String message) {
        super(message);
    }

    public DeduplicacaoException(String message, Throwable cause) {
        super(message, cause);
    }
}