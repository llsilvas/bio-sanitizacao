package bio.prodesp.deduplicacao.controller.dto;

import bio.prodesp.deduplicacao.commons.model.enums.StatusValidacao;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Response DTO para processamento de deduplicação
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeduplicacaoResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * ID da coleta processada
     */
    private String idColeta;

    /**
     * CPF do cidadão
     */
    private String cpf;

    /**
     * Status final da validação
     */
    private StatusValidacao status;

    /**
     * ID do encounter no ABIS (se cadastrado)
     */
    private String abisEncounterId;

    /**
     * Score do match (0-100)
     */
    private Double matchScore;

    /**
     * Motivo quando status = INCONCLUSIVA
     */
    private String motivoInconclusivo;

    /**
     * Motivo quando status = INVALIDA
     */
    private String motivoRejeicao;

    /**
     * Timestamp do processamento
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime dataProcessamento;

    /**
     * Mensagem adicional
     */
    private String mensagem;

    /**
     * Indica se a coleta já foi processada anteriormente (idempotência)
     */
    @Builder.Default
    private Boolean jaProcessado = false;

    /**
     * Indica se requer análise manual
     */
    private Boolean requerAnaliseManual;

    /**
     * Sistema de origem
     */
    private String sistemaOrigem;

    // ========== Factory Methods ==========

    public static DeduplicacaoResponse sucesso(String idColeta, String cpf, StatusValidacao status, String sistemaOrigem) {
        return DeduplicacaoResponse.builder()
                .idColeta(idColeta)
                .cpf(cpf)
                .status(status)
                .sistemaOrigem(sistemaOrigem)
                .dataProcessamento(LocalDateTime.now())
                .requerAnaliseManual(status != null && status.requerAnaliseManual())
                .build();
    }

    public static DeduplicacaoResponse erro(String idColeta, String cpf, String mensagem, String sistemaOrigem) {
        return DeduplicacaoResponse.builder()
                .idColeta(idColeta)
                .cpf(cpf)
                .status(StatusValidacao.ERRO_REPROCESSAVEL)
                .mensagem(mensagem)
                .sistemaOrigem(sistemaOrigem)
                .dataProcessamento(LocalDateTime.now())
                .requerAnaliseManual(true)
                .build();
    }

    public static DeduplicacaoResponse invalido(String idColeta, String cpf, String motivo, String sistemaOrigem) {
        return DeduplicacaoResponse.builder()
                .idColeta(idColeta)
                .cpf(cpf)
                .status(StatusValidacao.INVALIDA)
                .motivoRejeicao(motivo)
                .sistemaOrigem(sistemaOrigem)
                .dataProcessamento(LocalDateTime.now())
                .requerAnaliseManual(false)
                .build();
    }

    public static DeduplicacaoResponse jaProcessado(String idColeta, String cpf, String sistemaOrigem) {
        return DeduplicacaoResponse.builder()
                .idColeta(idColeta)
                .cpf(cpf)
                .jaProcessado(true)
                .mensagem("Coleta já processada anteriormente")
                .sistemaOrigem(sistemaOrigem)
                .dataProcessamento(LocalDateTime.now())
                .build();
    }
}