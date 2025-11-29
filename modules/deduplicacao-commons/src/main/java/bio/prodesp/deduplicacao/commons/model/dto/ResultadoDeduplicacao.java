package bio.prodesp.deduplicacao.commons.model.dto;

import bio.prodesp.deduplicacao.commons.model.enums.StatusValidacao;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Resultado do processamento de deduplicação
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResultadoDeduplicacao implements Serializable {

    private static final long serialVersionUID = 1L;

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
    @Builder.Default
    private LocalDateTime dataProcessamento = LocalDateTime.now();

    /**
     * Mensagem adicional
     */
    private String mensagem;

    /**
     * Indica se a coleta já foi processada anteriormente (idempotência)
     */
    @Builder.Default
    private Boolean jaProcessado = false;

    // ========== Factory Methods ==========

    public static ResultadoDeduplicacao sucesso(StatusValidacao status) {
        return ResultadoDeduplicacao.builder()
                .status(status)
                .build();
    }

    public static ResultadoDeduplicacao sucesso(StatusValidacao status, String mensagem) {
        return ResultadoDeduplicacao.builder()
                .status(status)
                .mensagem(mensagem)
                .build();
    }

    public static ResultadoDeduplicacao inconclusivo(String motivo) {
        return ResultadoDeduplicacao.builder()
                .status(StatusValidacao.INCONCLUSIVA)
                .motivoInconclusivo(motivo)
                .build();
    }

    public static ResultadoDeduplicacao invalido(String motivo) {
        return ResultadoDeduplicacao.builder()
                .status(StatusValidacao.INVALIDA)
                .motivoRejeicao(motivo)
                .build();
    }

    public static ResultadoDeduplicacao erro(StatusValidacao status, String mensagem) {
        return ResultadoDeduplicacao.builder()
                .status(status)
                .mensagem(mensagem)
                .build();
    }

    public static ResultadoDeduplicacao jaProcessado() {
        return ResultadoDeduplicacao.builder()
                .jaProcessado(true)
                .mensagem("Coleta já processada anteriormente")
                .build();
    }

    // ========== Helper Methods ==========

    public boolean isValida() {
        return status == StatusValidacao.VALIDA;
    }

    public boolean isInconclusiva() {
        return status == StatusValidacao.INCONCLUSIVA;
    }

    public boolean isInvalida() {
        return status == StatusValidacao.INVALIDA;
    }

    public boolean requerAnaliseManual() {
        return status != null && status.requerAnaliseManual();
    }
}