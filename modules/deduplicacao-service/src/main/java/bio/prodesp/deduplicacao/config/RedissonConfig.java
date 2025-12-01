package bio.prodesp.deduplicacao.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração do Redisson para distributed locks e cache distribuído.
 *
 * <p>O Redisson é usado para:
 * <ul>
 *   <li>Distributed locks - Sincronização entre múltiplas instâncias do serviço</li>
 *   <li>Cache distribuído - Compartilhamento de cache entre instâncias</li>
 *   <li>RedisTemplate - Integração com Spring Data Redis</li>
 * </ul>
 *
 * <p>A configuração usa variáveis de ambiente para permitir diferentes
 * configurações por ambiente (local, dev, staging, prod).
 *
 * @author Bio Sanitização Team
 * @since 1.0.0
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    /**
     * Cria e configura o cliente Redisson.
     *
     * <p>Configurações aplicadas:
     * <ul>
     *   <li>Connection pool: 20 conexões ativas, 5 mínimas idle</li>
     *   <li>Timeout: 3 segundos</li>
     *   <li>Retry: 3 tentativas com intervalo de 1.5s</li>
     * </ul>
     *
     * @return RedissonClient configurado e pronto para uso
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redisson() {
        Config config = new Config();

        // Configuração single server (não cluster)
        String address = String.format("redis://%s:%d", redisHost, redisPort);

        config.useSingleServer()
                .setAddress(address)
                .setPassword(redisPassword.isEmpty() ? null : redisPassword)
                .setConnectionPoolSize(20)
                .setConnectionMinimumIdleSize(5)
                .setTimeout(3000)
                .setRetryAttempts(3)
                .setRetryInterval(1500)
                .setConnectTimeout(3000)
                .setIdleConnectionTimeout(10000)
                .setPingConnectionInterval(30000);

        return Redisson.create(config);
    }
}
