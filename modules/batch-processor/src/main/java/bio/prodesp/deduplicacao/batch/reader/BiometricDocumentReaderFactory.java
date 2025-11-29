package br.gov.sp.prodesp.deduplicacao.batch.reader;

import br.gov.sp.prodesp.deduplicacao.batch.service.OpenSearchService;
import br.gov.sp.prodesp.deduplicacao.model.dto.BiometricDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Factory para criar ItemReader de documentos biométricos do OpenSearch
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class BiometricDocumentReaderFactory {

    private final OpenSearchService openSearchService;

    @Bean
    @StepScope
    public ItemReader<BiometricDocument> biometricDocumentItemReader(
            @Value("#{stepExecutionContext['partitionNumber']}") Integer partitionNumber,
            @Value("#{stepExecutionContext['totalPartitions']}") Integer totalPartitions) {

        log.info("Creating OpenSearch reader for partition {} of {}",
                partitionNumber, totalPartitions);

        return new OpenSearchItemReader(
                openSearchService,
                partitionNumber,
                totalPartitions
        );
    }
}