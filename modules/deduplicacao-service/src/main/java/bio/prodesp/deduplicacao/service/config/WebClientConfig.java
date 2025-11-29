package bio.prodesp.deduplicacao.service.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Configuração do WebClient para chamadas ao ABIS OSIA
 */
@Slf4j
@Configuration
public class WebClientConfig {

    @Value("${osia.base-url}")
    private String osiaBaseUrl;

    @Value("${osia.timeout.connect:10}")
    private int connectTimeout;

    @Value("${osia.timeout.read:30}")
    private int readTimeout;

    @Value("${osia.timeout.write:30}")
    private int writeTimeout;

    @Bean
    public WebClient webClient() {
        log.info("Configurando WebClient para OSIA - URL: {}", osiaBaseUrl);

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout * 1000)
                .responseTimeout(Duration.ofSeconds(readTimeout))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(readTimeout, TimeUnit.SECONDS))
                                .addHandlerLast(new WriteTimeoutHandler(writeTimeout, TimeUnit.SECONDS))
                );

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
