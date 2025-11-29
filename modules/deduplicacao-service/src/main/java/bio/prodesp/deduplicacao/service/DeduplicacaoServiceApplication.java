package bio.prodesp.deduplicacao.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class DeduplicacaoServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeduplicacaoServiceApplication.class, args);
    }
}
