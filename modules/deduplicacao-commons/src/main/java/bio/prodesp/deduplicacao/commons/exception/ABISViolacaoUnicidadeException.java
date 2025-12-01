package bio.prodesp.deduplicacao.commons.exception;

/**
 * Exception quando há violação de unicidade no ABIS (RN010)
 * HTTP 409 Conflict - biometria já cadastrada para outra pessoa
 */
public class ABISViolacaoUnicidadeException extends OSIAException {

    private static final long serialVersionUID = 1L;

    private final String conflictingPersonId;

    public ABISViolacaoUnicidadeException(String message) {
        super(message);
        this.conflictingPersonId = null;
    }

    public ABISViolacaoUnicidadeException(String message, Throwable cause) {
        super(message, cause);
        this.conflictingPersonId = null;
    }

    public ABISViolacaoUnicidadeException(String message, String conflictingPersonId) {
        super(message);
        this.conflictingPersonId = conflictingPersonId;
    }

    public String getConflictingPersonId() {
        return conflictingPersonId;
    }
}