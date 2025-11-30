package bio.prodesp.deduplicacao.service.idempotencia;

import bio.prodesp.deduplicacao.commons.model.dto.ResultadoDeduplicacao;

import java.util.Optional;

/**
 * Serviço de idempotência para prevenção de processamento duplicado
 *
 * <p>Implementa estratégia de dois níveis:
 * <ul>
 *   <li>L1 (Redis): Cache rápido para últimas 24h (&lt;5ms)</li>
 *   <li>L2 (OpenSearch): Fallback verificando campo 'processado' no documento</li>
 * </ul>
 *
 * <p>Garante que uma mesma coleta não seja processada múltiplas vezes,
 * mesmo em caso de falha do cache Redis.
 *
 * @author Bio Sanitização Team
 * @since 1.0.0
 */
public interface IdempotenciaService {

    /**
     * Verifica se operação já foi processada
     *
     * <p>Sequência de verificação:
     * <ol>
     *   <li>Consulta Redis (L1 cache)</li>
     *   <li>Se não encontrado, consulta OpenSearch (L2 fallback)</li>
     *   <li>Se encontrado no OpenSearch, popula Redis para próximas consultas</li>
     * </ol>
     *
     * @param idempotencyKey Chave única no formato "DEDUP:CPF:ID_COLETA"
     * @return Resultado anterior se já processado, Optional.empty() caso contrário
     */
    Optional<ResultadoDeduplicacao> verificar(String idempotencyKey);

    /**
     * Registra resultado de processamento no cache Redis
     *
     * @param idempotencyKey Chave única
     * @param resultado Resultado a ser armazenado
     * @param ttlMinutes Tempo de vida em minutos (default: 1440 = 24h)
     */
    void registrar(
        String idempotencyKey,
        ResultadoDeduplicacao resultado,
        long ttlMinutes
    );

    /**
     * Limpa registro de idempotência do Redis
     *
     * <p>Utilizado para forçar reprocessamento manual de uma coleta.
     *
     * @param idempotencyKey Chave única
     */
    void limpar(String idempotencyKey);

    /**
     * Gera chave de idempotência padrão no formato DEDUP:CPF:ID_COLETA
     *
     * @param cpf CPF do cidadão
     * @param idColeta ID da coleta
     * @return Chave de idempotência formatada
     */
    default String gerarChave(String cpf, String idColeta) {
        return String.format("DEDUP:%s:%s", cpf, idColeta);
    }
}