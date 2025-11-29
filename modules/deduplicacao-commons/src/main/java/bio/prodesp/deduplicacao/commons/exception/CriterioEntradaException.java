package bio.prodesp.deduplicacao.commons.exception;

/**
 * Exception quando a coleta não atende critérios de entrada (RN003)
 * Exemplos:
 * - validacao_pendente = true
 * - CPF inválido
 * - Idade <= 15 anos
 */
public class CriterioEntradaException extends DeduplicacaoException {

    private static final long serialVersionUID = 1L;

    public CriterioEntradaException(String message) {
        super(message);
    }

    public CriterioEntradaException(String message, Throwable cause) {
        super(message, cause);
    }
}