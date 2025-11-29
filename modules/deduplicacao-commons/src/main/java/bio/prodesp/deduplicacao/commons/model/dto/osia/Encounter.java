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
 * Encounter (coleta) retornado pelo ABIS OSIA
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Encounter implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * ID do encounter no ABIS
     */
    @JsonProperty("encounterId")
    private String encounterId;

    /**
     * Dados biométricos
     */
    @JsonProperty("biometricData")
    private BiometricDataOSIA biometricData;

    /**
     * Metadados do encounter
     */
    @JsonProperty("metadata")
    private EncounterMetadata metadata;

    /**
     * Status do encounter
     */
    @JsonProperty("status")
    private String status;
}