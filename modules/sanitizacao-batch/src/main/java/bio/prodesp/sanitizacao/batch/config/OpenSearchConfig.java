package bio.prodesp.deduplicacao.batch.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;

@Slf4j
@Configuration
public class OpenSearchConfig {

    @Value("${opensearch.host:localhost}")
    private String host;

    @Value("${opensearch.port:9200}")
    private int port;

    @Value("${opensearch.username:admin}")
    private String username;

    @Value("${opensearch.password:admin}")
    private String password;

    @Value("${opensearch.scheme:https}")
    private String scheme;

    @Value("${opensearch.ssl.verify:false}")
    private boolean verifySsl;

    @Bean
    public OpenSearchClient openSearchClient() {
        try {
            log.info("Initializing OpenSearch client: {}://{}:{}", scheme, host, port);

            HttpHost httpHost = new HttpHost(scheme, host, port);

            // Configuração de credenciais
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    new AuthScope(httpHost),
                    new UsernamePasswordCredentials(username, password.toCharArray())
            );

            // Configuração SSL
            SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial(null, (chains, authType) -> true)
                    .build();

            PoolingAsyncClientConnectionManager connectionManager = PoolingAsyncClientConnectionManagerBuilder
                    .create()
                    .setTlsStrategy(ClientTlsStrategyBuilder.create()
                            .setSslContext(sslContext)
                            .setHostnameVerifier((hostname, session) -> !verifySsl || true)
                            .build())
                    .setMaxConnTotal(100)
                    .setMaxConnPerRoute(20)
                    .build();

            // Construir o transport
            OpenSearchTransport transport = ApacheHttpClient5TransportBuilder
                    .builder(httpHost)
                    .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
                            .setDefaultCredentialsProvider(credentialsProvider)
                            .setConnectionManager(connectionManager))
                    .build();

            OpenSearchClient client = new OpenSearchClient(transport);

            log.info("OpenSearch client initialized successfully");
            return client;

        } catch (Exception e) {
            log.error("Failed to initialize OpenSearch client", e);
            throw new RuntimeException("Failed to initialize OpenSearch client", e);
        }
    }
}