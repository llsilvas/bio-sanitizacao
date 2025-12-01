package bio.prodesp.deduplicacao.batch;

import lombok.extern.java.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;

@Log
@SpringBootApplication
@EnableBatchProcessing
public class SanitizadorBatchApplication {
    public static void main(String[] args) {

        log.info(":: Iniciando Deduplicação-Batch ::");
        long startTime = System.currentTimeMillis(); // Captura o tempo de início
        SpringApplication.run(SanitizadorBatchApplication.class, args);
        long endTime = System.currentTimeMillis(); // Captura o tempo de fim
        long totalTime = endTime - startTime; // Calcula o tempo total em milissegundos
        log.info(":: Deduplicação-Batch iniciado com sucesso :: - " + totalTime + " ms" );
    }
}
