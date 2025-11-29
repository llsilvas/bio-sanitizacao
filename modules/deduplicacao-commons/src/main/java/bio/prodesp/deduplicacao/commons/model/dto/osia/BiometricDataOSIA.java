package bio.prodesp.deduplicacao.commons.model.dto.osia;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Dados biométricos no formato OSIA
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BiometricDataOSIA implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<FingerprintOSIA> fingerprints;
    private FaceImageOSIA face;
    private List<IrisImageOSIA> iris;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FingerprintOSIA implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * Posição do dedo
         * Exemplos: RIGHT_THUMB, LEFT_INDEX, RIGHT_MIDDLE, etc
         */
        private String position;

        /**
         * Imagem em Base64
         */
        private String image;

        /**
         * Formato da imagem
         * Exemplos: WSQ, PNG, JPEG
         */
        private String format;

        /**
         * Score de qualidade NFIQ2 (0-100)
         */
        private Integer quality;

        /**
         * Template biométrico (opcional)
         */
        private String template;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FaceImageOSIA implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * Imagem em Base64
         */
        private String image;

        /**
         * Formato da imagem
         * Exemplos: JPEG, PNG
         */
        private String format;

        /**
         * Score de qualidade (0-100)
         */
        private Integer quality;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class IrisImageOSIA implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * Posição: LEFT, RIGHT
         */
        private String position;

        /**
         * Imagem em Base64
         */
        private String image;

        /**
         * Formato da imagem
         */
        private String format;

        /**
         * Score de qualidade (0-100)
         */
        private Integer quality;
    }
}