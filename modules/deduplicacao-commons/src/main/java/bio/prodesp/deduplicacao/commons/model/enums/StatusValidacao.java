package bio.prodesp.deduplicacao.commons.model.enums;

/**
 * Status de validação da coleta biométrica após processamento OSIA
 */
public enum StatusValidacao {

    /**
     * Coleta válida - foi cadastrada no ABIS com sucesso
     */
    VALIDA("Válida"),

    /**
     * Coleta inconclusiva - requer análise manual
     * Cenários:
     * - IIRGD: Verify encontrou mas não deu match biométrico
     * - IIRGD: Identify 1:N encontrou 1 ou mais candidatos
     * - DETRAN: Sem coleta prévia no ABIS
     * - DETRAN: Verificação 1:1 sem match
     */
    INCONCLUSIVA("Inconclusiva"),

    /**
     * Coleta inválida - rejeitada
     * Cenários:
     * - Violação de unicidade no ABIS
     * - Erro não recuperável no processamento
     */
    INVALIDA("Inválida"),

    /**
     * Aguardando processamento
     */
    PENDENTE("Pendente"),

    /**
     * Erro recuperável - deve ser reprocessada
     */
    ERRO_REPROCESSAVEL("Erro Reprocessável");

    private final String descricao;

    StatusValidacao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    public boolean isProcessada() {
        return this == VALIDA || this == INCONCLUSIVA || this == INVALIDA;
    }

    public boolean requerAnaliseManual() {
        return this == INCONCLUSIVA;
    }
}