package bio.prodesp.deduplicacao.service.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * Request DTO para processamento de deduplicação
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeduplicacaoRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * ID único da coleta (idempotência)
     */
    @NotBlank(message = "idColeta é obrigatório")
    private String idColeta;

    /**
     * CPF do cidadão (11 dígitos)
     */
    @NotBlank(message = "CPF é obrigatório")
    @Pattern(regexp = "\\d{11}", message = "CPF deve conter 11 dígitos")
    private String cpf;

    /**
     * Data de nascimento
     */
    @NotNull(message = "dataNascimento é obrigatória")
    private Date dataNascimento;

    /**
     * Sistema de origem (IIRGD, DETRAN, etc.)
     */
    @NotBlank(message = "sistemaOrigem é obrigatório")
    private String sistemaOrigem;

    /**
     * Dados biométricos (digitais)
     */
    private List<DadoBiometricoDTO> dadosBiometricos;

    /**
     * DTO para dados biométricos individuais
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DadoBiometricoDTO {

        /**
         * Tipo: IMPRESSAO_DIGITAL, FACE, IRIS
         */
        @NotBlank(message = "tipo é obrigatório")
        private String tipo;

        /**
         * Posição (para digitais: POLEGAR_DIREITO, INDICADOR_ESQUERDO, etc.)
         */
        private String posicao;

        /**
         * Template biométrico em Base64
         */
        @NotBlank(message = "template é obrigatório")
        private String template;

        /**
         * Score de qualidade NFIQ2 (0-100)
         */
        private Integer nfiq2Score;

        /**
         * Formato do template (WSQ, PNG, ISO_19794_2, etc.)
         */
        private String formato;
    }
}