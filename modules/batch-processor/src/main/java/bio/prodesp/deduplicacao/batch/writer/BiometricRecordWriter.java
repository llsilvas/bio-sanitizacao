package br.gov.sp.prodesp.deduplicacao.batch.writer;

import br.gov.sp.prodesp.deduplicacao.model.domain.BiometricRecord;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ItemWriter para persistir registros biométricos processados
 */
@Slf4j
@Configuration
public class BiometricRecordWriter {

    @Bean
    @StepScope
    public JpaItemWriter<BiometricRecord> biometricRecordItemWriter(
            EntityManagerFactory entityManagerFactory,
            @Value("#{stepExecutionContext['partitionNumber']}") Integer partitionNumber) {

        log.info("Initializing writer for partition {}", partitionNumber);

        return new JpaItemWriterBuilder<BiometricRecord>()
                .entityManagerFactory(entityManagerFactory)
                .usePersist(false) // Use merge instead of persist
                .build();
    }
}