package bio.prodesp.deduplicacao.commons.model.dto.osia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Response do POST /v1/verify/ALL/{cpf}
 * Verificação 1:1 (biometria enviada vs biometria cadastrada no CPF)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class VerifyResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Indica se a biometria foi verificada com sucesso
     * true = match encontrado
     * false = sem match ou pessoa não encontrada
     */
    @JsonProperty("verified")
    private Boolean verified;

    /**
     * Score do match (0-100)
     * null se pessoa não foi encontrada
     */
    @JsonProperty("score")
    private Double score;

    /**
     * ID do encounter que deu match
     * null se não houve match
     */
    @JsonProperty("encounterId")
    private String encounterId;

    /**
     * Verifica se a pessoa foi encontrada no ABIS
     * (mesmo que não tenha dado match biométrico)
     */
    public boolean isPessoaEncontrada() {
        return encounterId != null;
    }

    /**
     * Verifica se houve match biométrico
     */
    public boolean isVerified() {
        return Boolean.TRUE.equals(verified);
    }
}