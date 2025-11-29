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
 * Candidato retornado na busca 1:N
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Candidate implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * CPF do candidato
     */
    @JsonProperty("personId")
    private String personId;

    /**
     * ID do encounter que deu match
     */
    @JsonProperty("encounterId")
    private String encounterId;

    /**
     * Score do match (0-100)
     */
    @JsonProperty("score")
    private Double score;
}