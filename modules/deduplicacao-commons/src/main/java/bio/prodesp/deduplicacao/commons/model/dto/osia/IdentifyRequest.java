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
 * Request para POST /v1/identify
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdentifyRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("biometricData")
    private BiometricDataOSIA biometricData;

    @JsonProperty("filters")
    private IdentifyFilters filters;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class IdentifyFilters implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * Máximo de candidatos a retornar
         */
        @JsonProperty("maxCandidates")
        @Builder.Default
        private Integer maxCandidates = 10;

        /**
         * Threshold mínimo de score (0-100)
         */
        @JsonProperty("threshold")
        @Builder.Default
        private Integer threshold = 70;
    }
}