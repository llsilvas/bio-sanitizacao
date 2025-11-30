package bio.prodesp.deduplicacao;

import lombok.extern.java.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@Log
@SpringBootApplication
@EnableCaching
public class DeduplicacaoServiceApplication {
    public static void main(String[] args) {

        log.info(":: Iniciando Deduplicação-Service ::");
        long startTime = System.currentTimeMillis(); // Captura o tempo de início
        SpringApplication.run(DeduplicacaoServiceApplication.class, args);

        long endTime = System.currentTimeMillis(); // Captura o tempo de fim
        long totalTime = endTime - startTime; // Calcula o tempo total em milissegundos
        log.info(":: Deduplicação-Service iniciado com sucesso :: - " + totalTime + " ms" );
    }
}
