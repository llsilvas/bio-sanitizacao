package br.gov.sp.prodesp.deduplicacao.batch.reader;

import br.gov.sp.prodesp.deduplicacao.model.domain.BiometricRecord;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * ItemReader para leitura paginada de registros biométricos
 */
@Slf4j
@Configuration
public class BiometricRecordReader {

    @Bean
    @StepScope
    public JpaPagingItemReader<BiometricRecord> biometricRecordItemReader(
            EntityManagerFactory entityManagerFactory,
            @Value("#{stepExecutionContext['partitionNumber']}") Integer partitionNumber,
            @Value("#{stepExecutionContext['totalPartitions']}") Integer totalPartitions,
            @Value("${batch.chunk-size:100}") Integer pageSize) {

        log.info("Initializing CPF-based reader for partition {} of {}",
                partitionNumber, totalPartitions);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("partitionNumber", partitionNumber);
        parameters.put("totalPartitions", totalPartitions);

        return new JpaPagingItemReaderBuilder<BiometricRecord>()
                .name("biometricRecordItemReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("SELECT r FROM BiometricRecord r " +
                        "WHERE r.processed = false " +
                        "AND MOD(ABS(CHECKSUM(r.documentNumber)), :totalPartitions) = :partitionNumber " +
                        "ORDER BY r.documentNumber, r.id ASC")
                .parameterValues(parameters)
                .pageSize(pageSize)
                .saveState(true)
                .build();
    }
}