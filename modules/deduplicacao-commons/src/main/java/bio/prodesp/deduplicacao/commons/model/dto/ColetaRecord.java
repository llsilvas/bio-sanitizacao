package bio.prodesp.deduplicacao.commons.model.dto;

import bio.prodesp.deduplicacao.commons.model.domain.ColetaMetadata;
import bio.prodesp.deduplicacao.commons.model.enums.StatusValidacao;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DTO para encapsular ColetaMetadata durante processamento batch
 * Adiciona metadados de controle do processamento
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ColetaRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Dados da coleta biométrica do OpenSearch
     */
    private ColetaMetadata coleta;

    /**
     * Número da partição que está processando este registro
     */
    private Integer partitionNumber;

    /**
     * Status de validação após processamento
     */
    private StatusValidacao statusValidacao;

    /**
     * ID do encounter no ABIS (após cadastro)
     */
    private String abisEncounterId;

    /**
     * Score do match (0-100)
     */
    private Double matchScore;

    /**
     * Indica se foi processado com sucesso
     */
    @Builder.Default
    private Boolean processado = false;

    /**
     * Motivo quando inconclusivo
     */
    private String motivoInconclusivo;

    /**
     * Motivo quando rejeitado
     */
    private String motivoRejeicao;

    /**
     * Mensagem de erro (se houver)
     */
    private String mensagemErro;

    // ========== Factory Methods ==========

    /**
     * Cria um ColetaRecord a partir de ColetaMetadata lido do OpenSearch
     */
    public static ColetaRecord from(ColetaMetadata coleta, Integer partitionNumber) {
        return ColetaRecord.builder()
                .coleta(coleta)
                .partitionNumber(partitionNumber)
                .processado(false)
                .build();
    }

    // ========== Helper Methods ==========

    public String getIdColeta() {
        return coleta != null ? coleta.getIdColeta() : null;
    }

    public String getCpf() {
        return coleta != null ? coleta.getCpf() : null;
    }

    public boolean isProcessado() {
        return Boolean.TRUE.equals(processado);
    }

    public boolean isValido() {
        return statusValidacao == StatusValidacao.VALIDA;
    }

    public boolean isInconclusivo() {
        return statusValidacao == StatusValidacao.INCONCLUSIVA;
    }

    public boolean isInvalido() {
        return statusValidacao == StatusValidacao.INVALIDA;
    }
}