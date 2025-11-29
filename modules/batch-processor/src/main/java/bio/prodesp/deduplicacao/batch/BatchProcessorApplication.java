package br.gov.sp.prodesp.deduplicacao.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;

@SpringBootApplication
@EnableBatchProcessing
public class BatchProcessorApplication {
    public static void main(String[] args) {
        SpringApplication.run(BatchProcessorApplication.class, args);
    }
}
