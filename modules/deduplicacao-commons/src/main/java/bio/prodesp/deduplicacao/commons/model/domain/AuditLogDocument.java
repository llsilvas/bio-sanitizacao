package bio.prodesp.deduplicacao.commons.model.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Documento de auditoria para registro de eventos no OpenSearch
 *
 * <p>Utilizado para rastreabilidade de operações no sistema de deduplicação biométrica.
 * Garante compliance com LGPD através de audit trail completo.
 *
 * <p>Os documentos são armazenados em índices com rollover diário no formato:
 * {@code audit-logs-YYYY.MM.DD}
 *
 * @author Bio Sanitização Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuditLogDocument implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * ID único do log (gerado automaticamente pelo OpenSearch)
     */
    private String id;

    /**
     * Timestamp do evento (usado para ordenação e rollover)
     * Formato: ISO-8601 (yyyy-MM-dd'T'HH:mm:ss)
     */
    private LocalDateTime timestamp;

    /**
     * Tipo de evento
     *
     * <p>Exemplos:
     * <ul>
     *   <li>PROCESSAMENTO_CONCLUIDO</li>
     *   <li>MUDANCA_STATUS</li>
     *   <li>CADASTRO_ABIS</li>
     *   <li>ERRO_OSIA</li>
     *   <li>ERRO_PROCESSAMENTO</li>
     *   <li>VIOLACAO_UNICIDADE</li>
     * </ul>
     */
    private String evento;

    /**
     * CPF do cidadão (para rastreabilidade)
     * Formato: apenas dígitos (11 caracteres)
     */
    private String cpf;

    /**
     * ID da coleta biométrica
     */
    private String idColeta;

    /**
     * Detalhes adicionais do evento
     *
     * <p>Pode conter JSON string ou texto livre.
     * Exemplos:
     * <ul>
     *   <li>{"statusAnterior":"VALIDA","statusNovo":"INCONCLUSIVA","motivo":"Match em 1:N"}</li>
     *   <li>{"encounterId":"ABC123","operacao":"ENROLL"}</li>
     *   <li>{"erro":"TimeoutException","mensagem":"OSIA timeout após 30s"}</li>
     * </ul>
     */
    private String detalhes;

    /**
     * Nível de severidade do evento
     *
     * <p>Valores possíveis:
     * <ul>
     *   <li>INFO - Operações normais</li>
     *   <li>WARN - Situações que requerem atenção</li>
     *   <li>ERROR - Erros que afetam processamento</li>
     * </ul>
     */
    private String nivel;

    /**
     * Usuário ou sistema que originou o evento
     *
     * <p>Exemplos:
     * <ul>
     *   <li>deduplicacao-service</li>
     *   <li>batch-processor</li>
     *   <li>admin-user-12345</li>
     * </ul>
     */
    private String origem;

    /**
     * ID do processo batch (se aplicável)
     * Usado para correlacionar eventos de execuções batch
     */
    private String batchJobId;

    /**
     * Número da partição (se processamento paralelo)
     * Range: 0 a (gridSize - 1)
     */
    private Integer partitionNumber;

    /**
     * Duração da operação em milissegundos (para métricas de performance)
     *
     * <p>Útil para identificar operações lentas e gargalos.
     * Exemplo: 1523 (1.523 segundos)
     */
    private Long duracaoMs;

    /**
     * IP ou hostname da máquina que gerou o evento
     * Útil para debugging em ambientes distribuídos
     */
    private String host;

    /**
     * Versão da aplicação que gerou o evento
     * Formato: semver (ex: 1.0.0-SNAPSHOT)
     */
    private String versaoApp;
}