package bio.prodesp.deduplicacao.commons.model.enums;

/**
 * Origem da coleta biométrica
 */
public enum OrigemColeta {

    /**
     * Instituto de Identificação Ricardo Gumbleton Daunt
     * Responsável por emissão de RG
     */
    IIRGD("IIRGD", "Instituto de Identificação"),

    /**
     * Departamento Estadual de Trânsito
     * Responsável por emissão de CNH
     */
    DETRAN("DETRAN", "Departamento de Trânsito"),

    /**
     * Poupatempo - Atendimento integrado
     */
    POUPATEMPO("POUPATEMPO", "Poupatempo"),

    /**
     * Sistema de Migração/Importação
     */
    MIGRACAO("MIGRACAO", "Migração");

    private final String codigo;
    private final String descricao;

    OrigemColeta(String codigo, String descricao) {
        this.codigo = codigo;
        this.descricao = descricao;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescricao() {
        return descricao;
    }

    public static OrigemColeta fromCodigo(String codigo) {
        for (OrigemColeta origem : values()) {
            if (origem.codigo.equalsIgnoreCase(codigo)) {
                return origem;
            }
        }
        throw new IllegalArgumentException("Origem inválida: " + codigo);
    }

    /**
     * DETRAN requer coleta prévia no ABIS (regra RN015)
     */
    public boolean requerColetaPrevia() {
        return this == DETRAN;
    }
}