package bio.prodesp.deduplicacao.service.idempotencia;

import bio.prodesp.deduplicacao.commons.model.dto.ResultadoDeduplicacao;
import bio.prodesp.deduplicacao.commons.model.enums.StatusValidacao;
import bio.prodesp.deduplicacao.repository.ColetaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Implementação do serviço de idempotência com estratégia de dois níveis
 *
 * <p>L1 (Redis): Cache rápido para resultados recentes (24h)
 * <p>L2 (OpenSearch): Fallback verificando status do documento
 *
 * @author Bio Sanitização Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotenciaServiceImpl implements IdempotenciaService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ColetaRepository coletaRepository;
    private final ObjectMapper objectMapper;

    private static final String REDIS_PREFIX = "idempotency:";
    private static final long DEFAULT_TTL_MINUTES = 1440; // 24 horas

    @Override
    public Optional<ResultadoDeduplicacao> verificar(String idempotencyKey) {
        // NÍVEL 1: Verificar Redis (rápido)
        Optional<ResultadoDeduplicacao> resultadoRedis = verificarRedis(idempotencyKey);
        if (resultadoRedis.isPresent()) {
            log.debug("Idempotência encontrada no Redis - Key: {}", idempotencyKey);
            return resultadoRedis;
        }

        // NÍVEL 2: Verificar OpenSearch (fallback)
        Optional<ResultadoDeduplicacao> resultadoOpenSearch = verificarOpenSearch(idempotencyKey);
        if (resultadoOpenSearch.isPresent()) {
            log.info("Idempotência encontrada no OpenSearch (cache miss) - Key: {}", idempotencyKey);
            // Popular Redis para próximas consultas
            registrar(idempotencyKey, resultadoOpenSearch.get(), DEFAULT_TTL_MINUTES);
            return resultadoOpenSearch;
        }

        return Optional.empty();
    }

    /**
     * Verifica idempotência no Redis (L1 cache)
     */
    private Optional<ResultadoDeduplicacao> verificarRedis(String idempotencyKey) {
        try {
            String key = REDIS_PREFIX + idempotencyKey;
            String json = redisTemplate.opsForValue().get(key);

            if (json != null) {
                ResultadoDeduplicacao resultado = objectMapper.readValue(
                    json,
                    ResultadoDeduplicacao.class
                );
                return Optional.of(resultado);
            }

            return Optional.empty();

        } catch (Exception e) {
            log.error("Erro ao verificar idempotência no Redis - Key: {}", idempotencyKey, e);
            return Optional.empty(); // Fail-open
        }
    }

    /**
     * Verifica idempotência no OpenSearch (L2 fallback)
     */
    private Optional<ResultadoDeduplicacao> verificarOpenSearch(String idempotencyKey) {
        try {
            // Idempotency key format: "DEDUP:CPF:ID_COLETA"
            String[] parts = idempotencyKey.split(":");
            if (parts.length != 3) {
                log.warn("Formato inválido de idempotency key: {}", idempotencyKey);
                return Optional.empty();
            }

            String idColeta = parts[2];
            Optional<Map<String, Object>> coletaOpt = coletaRepository.buscarPorId(idColeta);

            if (coletaOpt.isEmpty()) {
                return Optional.empty();
            }

            Map<String, Object> coleta = coletaOpt.get();

            // Verificar se já foi processada com sucesso
            Boolean processado = (Boolean) coleta.get("processado");
            String statusStr = (String) coleta.get("status");

            if (Boolean.TRUE.equals(processado) && statusStr != null) {
                StatusValidacao status = StatusValidacao.valueOf(statusStr);

                // Apenas considerar como já processado se não for erro reprocessável
                if (status != StatusValidacao.ERRO_REPROCESSAVEL) {
                    log.info("Coleta já processada anteriormente - ID: {}, Status: {}",
                            idColeta, status);

                    // Reconstruir resultado a partir do documento
                    return Optional.of(ResultadoDeduplicacao.builder()
                            .status(status)
                            .abisEncounterId((String) coleta.get("abisRecordId"))
                            .motivoInconclusivo((String) coleta.get("motivoInconclusivo"))
                            .motivoRejeicao((String) coleta.get("motivoRejeicao"))
                            .mensagem("Resultado recuperado do histórico")
                            .build());
                }
            }

            return Optional.empty();

        } catch (Exception e) {
            log.error("Erro ao verificar idempotência no OpenSearch - Key: {}", idempotencyKey, e);
            return Optional.empty(); // Fail-open
        }
    }

    @Override
    public void registrar(
            String idempotencyKey,
            ResultadoDeduplicacao resultado,
            long ttlMinutes) {

        try {
            String key = REDIS_PREFIX + idempotencyKey;
            String json = objectMapper.writeValueAsString(resultado);

            redisTemplate.opsForValue().set(
                key,
                json,
                Duration.ofMinutes(ttlMinutes)
            );

            log.debug("Idempotência registrada no Redis - Key: {}, TTL: {}min",
                     idempotencyKey, ttlMinutes);

        } catch (Exception e) {
            log.error("Erro ao registrar idempotência - Key: {}", idempotencyKey, e);
            // Não falha o fluxo principal
        }
    }

    @Override
    public void limpar(String idempotencyKey) {
        try {
            String key = REDIS_PREFIX + idempotencyKey;
            Boolean deleted = redisTemplate.delete(key);

            log.info("Idempotência removida do Redis - Key: {}, Sucesso: {}",
                    idempotencyKey, deleted);

        } catch (Exception e) {
            log.error("Erro ao limpar idempotência - Key: {}", idempotencyKey, e);
        }
    }
}