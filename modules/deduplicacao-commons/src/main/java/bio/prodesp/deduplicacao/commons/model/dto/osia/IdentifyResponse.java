package bio.prodesp.deduplicacao.commons.model.dto.osia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Response do POST /v1/identify
 * Identificação 1:N (busca biometria na base inteira)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdentifyResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Lista de candidatos encontrados
     */
    @JsonProperty("candidates")
    private List<Candidate> candidates;

    /**
     * Total de matches encontrados
     */
    @JsonProperty("totalMatches")
    private Integer totalMatches;

    public int getTotalMatches() {
        return totalMatches != null ? totalMatches : (candidates != null ? candidates.size() : 0);
    }

    public boolean hasCandidates() {
        return candidates != null && !candidates.isEmpty();
    }
}