package bio.prodesp.deduplicacao.batch.processor;

import br.gov.sp.prodesp.deduplicacao.model.dto.BiometricDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Processor para processar e deduplicar documentos biométricos do OpenSearch
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class BiometricDocumentProcessor implements ItemProcessor<BiometricDocument, BiometricDocument> {

    private final RestTemplate restTemplate;

    @Value("${deduplicacao.service.url:http://localhost:8080}")
    private String deduplicacaoServiceUrl;

    @Value("#{stepExecutionContext['partitionNumber']}")
    private Integer partitionNumber;

    @Override
    public BiometricDocument process(BiometricDocument document) throws Exception {
        log.debug("Processing document ID {} (CPF: {}) in partition {}",
                document.getId(), document.getDocumentNumber(), partitionNumber);

        try {
            // Chama o serviço de deduplicação para verificar duplicatas
            DeduplicationResult result = callDeduplicationService(document);

            if (result != null && result.isDuplicate()) {
                document.setDuplicateOf(result.getOriginalRecordId());
                document.setMatchScore(result.getMatchScore());
                document.setRecordStatus("DUPLICATE");
                log.info("Document {} (CPF: {}) marked as duplicate of {} with score {}",
                        document.getId(),
                        document.getDocumentNumber(),
                        result.getOriginalRecordId(),
                        result.getMatchScore());
            } else {
                document.setRecordStatus("UNIQUE");
                log.debug("Document {} (CPF: {}) marked as unique",
                        document.getId(),
                        document.getDocumentNumber());
            }

            document.setProcessed(true);
            document.setProcessedAt(LocalDateTime.now());
            document.setUpdatedAt(LocalDateTime.now());

            return document;

        } catch (Exception e) {
            log.error("Error processing document {} (CPF: {}): {}",
                    document.getId(),
                    document.getDocumentNumber(),
                    e.getMessage(), e);
            document.setRecordStatus("ERROR");
            document.setProcessed(false);
            throw e;
        }
    }

    /**
     * Chama o serviço de deduplicação via REST
     */
    private DeduplicationResult callDeduplicationService(BiometricDocument document) {
        try {
            String url = deduplicacaoServiceUrl + "/api/v1/deduplication/check";

            DeduplicationRequest request = DeduplicationRequest.builder()
                    .documentId(document.getId())
                    .documentNumber(document.getDocumentNumber())
                    .fingerprintTemplate(decodeBase64(document.getFingerprintTemplate()))
                    .faceTemplate(decodeBase64(document.getFaceTemplate()))
                    .irisTemplate(decodeBase64(document.getIrisTemplate()))
                    .build();

            return restTemplate.postForObject(url, request, DeduplicationResult.class);

        } catch (Exception e) {
            log.warn("Failed to call deduplication service for document {} (CPF: {}): {}",
                    document.getId(),
                    document.getDocumentNumber(),
                    e.getMessage());
            return null;
        }
    }

    /**
     * Decodifica string Base64 para byte array
     */
    private byte[] decodeBase64(String base64String) {
        if (base64String == null || base64String.isEmpty()) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(base64String);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to decode base64 string: {}", e.getMessage());
            return null;
        }
    }

    // DTOs internos
    @lombok.Data
    @lombok.Builder
    private static class DeduplicationRequest {
        private String documentId;
        private String documentNumber;
        private byte[] fingerprintTemplate;
        private byte[] faceTemplate;
        private byte[] irisTemplate;
    }

    @lombok.Data
    private static class DeduplicationResult {
        private boolean duplicate;
        private String originalRecordId;
        private Double matchScore;
    }
}