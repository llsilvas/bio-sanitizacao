package br.gov.sp.prodesp.deduplicacao.batch.processor;

import br.gov.sp.prodesp.deduplicacao.model.domain.BiometricRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

/**
 * Processor para processar e deduplic registros biométricos
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class BiometricRecordProcessor implements ItemProcessor<BiometricRecord, BiometricRecord> {

    private final RestTemplate restTemplate;

    @Value("${deduplicacao.service.url:http://localhost:8080}")
    private String deduplicacaoServiceUrl;

    @Value("#{stepExecutionContext['partitionNumber']}")
    private Integer partitionNumber;

    @Override
    public BiometricRecord process(BiometricRecord record) throws Exception {
        log.debug("Processing record ID {} in partition {}", record.getId(), partitionNumber);

        try {
            // Chama o serviço de deduplicação para verificar duplicatas
            DeduplicationResult result = callDeduplicationService(record);

            if (result != null && result.isDuplicate()) {
                record.setDuplicateOf(result.getOriginalRecordId());
                record.setMatchScore(result.getMatchScore());
                record.setRecordStatus("DUPLICATE");
                log.info("Record {} marked as duplicate of {} with score {}",
                        record.getId(), result.getOriginalRecordId(), result.getMatchScore());
            } else {
                record.setRecordStatus("UNIQUE");
                log.debug("Record {} marked as unique", record.getId());
            }

            record.setProcessed(true);
            record.setProcessedAt(LocalDateTime.now());

            return record;

        } catch (Exception e) {
            log.error("Error processing record {}: {}", record.getId(), e.getMessage(), e);
            record.setRecordStatus("ERROR");
            record.setProcessed(false);
            throw e;
        }
    }

    /**
     * Chama o serviço de deduplicação via REST
     */
    private DeduplicationResult callDeduplicationService(BiometricRecord record) {
        try {
            String url = deduplicacaoServiceUrl + "/api/v1/deduplication/check";

            DeduplicationRequest request = DeduplicationRequest.builder()
                    .recordId(record.getId())
                    .documentNumber(record.getDocumentNumber())
                    .fingerprintTemplate(record.getFingerprintTemplate())
                    .faceTemplate(record.getFaceTemplate())
                    .irisTemplate(record.getIrisTemplate())
                    .build();

            return restTemplate.postForObject(url, request, DeduplicationResult.class);

        } catch (Exception e) {
            log.warn("Failed to call deduplication service for record {}: {}",
                    record.getId(), e.getMessage());
            return null;
        }
    }

    // DTOs internos
    @lombok.Data
    @lombok.Builder
    private static class DeduplicationRequest {
        private Long recordId;
        private String documentNumber;
        private byte[] fingerprintTemplate;
        private byte[] faceTemplate;
        private byte[] irisTemplate;
    }

    @lombok.Data
    private static class DeduplicationResult {
        private boolean duplicate;
        private Long originalRecordId;
        private Double matchScore;
    }
}