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
 * Response do endpoint GET /v1/persons/{cpf}/encounters
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EncountersResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Lista de encounters encontrados
     */
    @JsonProperty("encounters")
    private List<Encounter> encounters;

    /**
     * Total de encounters
     */
    @JsonProperty("totalCount")
    private Integer totalCount;

    public int getTotalCount() {
        return totalCount != null ? totalCount : (encounters != null ? encounters.size() : 0);
    }

    public boolean hasEncounters() {
        return encounters != null && !encounters.isEmpty();
    }
}