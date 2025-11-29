package bio.prodesp.deduplicacao.commons.model.dto.osia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Metadados do encounter no ABIS
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EncounterMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * ID da coleta no sistema legado
     */
    @JsonProperty("idColetaLegado")
    private String idColetaLegado;

    /**
     * Origem da coleta (IIRGD, DETRAN)
     */
    @JsonProperty("origem")
    private String origem;

    /**
     * Score NFIQ2 (0-100)
     */
    @JsonProperty("nfiq2Score")
    private Integer nfiq2Score;

    /**
     * Data da coleta
     */
    @JsonProperty("dataColeta")
    private LocalDateTime dataColeta;

    /**
     * Operador que realizou a coleta
     */
    @JsonProperty("operador")
    private String operador;

    /**
     * Local da coleta
     */
    @JsonProperty("localColeta")
    private String localColeta;
}