package br.gov.sp.prodesp.deduplicacao.batch.writer;

import br.gov.sp.prodesp.deduplicacao.model.dto.BiometricDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Factory para criar ItemWriter de documentos biométricos para OpenSearch
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class BiometricDocumentWriterFactory {

    private final OpenSearchClient openSearchClient;

    @Value("${opensearch.index:biometric-data}")
    private String indexName;

    @Bean
    @StepScope
    public ItemWriter<BiometricDocument> biometricDocumentItemWriter(
            @Value("#{stepExecutionContext['partitionNumber']}") Integer partitionNumber) {

        log.info("Creating OpenSearch writer for partition {}", partitionNumber);

        return new OpenSearchItemWriter(
                openSearchClient,
                indexName,
                partitionNumber
        );
    }
}