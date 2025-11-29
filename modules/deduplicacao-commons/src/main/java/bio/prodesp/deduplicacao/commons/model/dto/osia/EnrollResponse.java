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
 * Response do POST /v1/persons/{cpf}/encounters/{id}
 * Cadastro de nova coleta no ABIS
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EnrollResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * ID do encounter criado no ABIS
     */
    @JsonProperty("encounterId")
    private String encounterId;

    /**
     * CPF da pessoa
     */
    @JsonProperty("personId")
    private String personId;

    /**
     * Status do cadastro
     * Valores: ENROLLED, PENDING, FAILED
     */
    @JsonProperty("status")
    private String status;

    public boolean isEnrolled() {
        return "ENROLLED".equalsIgnoreCase(status);
    }
}