package bio.prodesp.deduplicacao.repository;

import bio.prodesp.deduplicacao.commons.model.domain.AuditLogDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Repository para logs de auditoria no OpenSearch
 *
 * <p>Utiliza índices com rollover diário para otimizar performance e gestão de retenção.
 * Formato dos índices: {@code audit-logs-YYYY.MM.DD}
 *
 * <p>Características:
 * <ul>
 *   <li>Escrita assíncrona (não bloqueia fluxo principal)</li>
 *   <li>Bulk operations para alta performance</li>
 *   <li>Rollover diário automático</li>
 *   <li>ILM policy para purge de logs antigos</li>
 *   <li>Fail-safe: erros de auditoria NÃO afetam processamento principal</li>
 * </ul>
 *
 * @author Bio Sanitização Team
 * @since 1.0.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AuditRepository {

    private final OpenSearchClient openSearchClient;

    @Value("${opensearch.audit.index.prefix:audit-logs}")
    private String indexPrefix;

    @Value("${spring.application.name:deduplicacao-service}")
    private String applicationName;

    @Value("${app.version:1.0.0-SNAPSHOT}")
    private String appVersion;

    private static final DateTimeFormatter INDEX_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    /**
     * Registra evento de auditoria no OpenSearch
     *
     * <p>O documento será indexado no índice correspondente ao dia do timestamp.
     * Se timestamp não informado, utiliza LocalDateTime.now().
     *
     * @param auditLog Documento de auditoria
     * @return ID do documento criado (ou null em caso de erro)
     */
    public String registrar(AuditLogDocument auditLog) {
        try {
            // Preencher campos padrão se não informados
            enriquecerDocumento(auditLog);

            // Gera nome do índice com rollover diário
            String indexName = gerarNomeIndice(auditLog.getTimestamp());

            // Indexa documento
            IndexRequest<AuditLogDocument> request = IndexRequest.of(i -> i
                .index(indexName)
                .document(auditLog)
                .refresh(Refresh.False) // Não força refresh (melhor performance)
            );

            IndexResponse response = openSearchClient.index(request);

            log.debug("Auditoria registrada - Index: {}, ID: {}, Evento: {}",
                     indexName, response.id(), auditLog.getEvento());

            return response.id();

        } catch (IOException e) {
            log.error("Erro ao registrar auditoria no OpenSearch - Evento: {}, CPF: {}",
                     auditLog.getEvento(), auditLog.getCpf(), e);
            // Não falha o fluxo principal
            return null;
        }
    }

    /**
     * Registra múltiplos eventos em batch (bulk API)
     *
     * <p>Otimiza performance quando há múltiplos eventos para auditar.
     * Útil em processamento batch ou ao final de operações complexas.
     *
     * <p>Exemplo de uso:
     * <pre>{@code
     * List<AuditLogDocument> logs = Arrays.asList(
     *     AuditLogDocument.builder().evento("EVENTO_1").build(),
     *     AuditLogDocument.builder().evento("EVENTO_2").build()
     * );
     * int sucessos = auditRepository.registrarEmLote(logs);
     * }</pre>
     *
     * @param logs Lista de eventos de auditoria
     * @return Número de eventos registrados com sucesso
     */
    public int registrarEmLote(List<AuditLogDocument> logs) {
        if (logs == null || logs.isEmpty()) {
            log.warn("Tentativa de registrar lote vazio de auditoria");
            return 0;
        }

        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

            for (AuditLogDocument log : logs) {
                // Preencher campos padrão
                enriquecerDocumento(log);

                String indexName = gerarNomeIndice(log.getTimestamp());

                bulkBuilder.operations(op -> op
                    .index(idx -> idx
                        .index(indexName)
                        .document(log)
                    )
                );
            }

            BulkResponse response = openSearchClient.bulk(bulkBuilder.build());

            int sucessos = (int) response.items().stream()
                .filter(item -> item.error() == null)
                .count();

            int erros = logs.size() - sucessos;

            if (erros > 0) {
                log.warn("Auditoria em lote - Total: {}, Sucessos: {}, Erros: {}",
                        logs.size(), sucessos, erros);
            } else {
                log.debug("Auditoria em lote - Total: {}, Sucessos: {}",
                        logs.size(), sucessos);
            }

            return sucessos;

        } catch (IOException e) {
            log.error("Erro ao registrar auditoria em lote - Total eventos: {}", logs.size(), e);
            return 0;
        }
    }

    /**
     * Enriquece documento com campos padrão do sistema
     *
     * <p>Preenche automaticamente:
     * <ul>
     *   <li>timestamp (se null)</li>
     *   <li>origem (application name)</li>
     *   <li>versaoApp</li>
     *   <li>host (hostname da máquina)</li>
     *   <li>nivel (default: INFO se null)</li>
     * </ul>
     *
     * @param auditLog Documento a ser enriquecido
     */
    private void enriquecerDocumento(AuditLogDocument auditLog) {
        // Timestamp
        if (auditLog.getTimestamp() == null) {
            auditLog.setTimestamp(LocalDateTime.now());
        }

        // Origem (nome da aplicação)
        if (auditLog.getOrigem() == null) {
            auditLog.setOrigem(applicationName);
        }

        // Versão da aplicação
        if (auditLog.getVersaoApp() == null) {
            auditLog.setVersaoApp(appVersion);
        }

        // Hostname
        if (auditLog.getHost() == null) {
            try {
                auditLog.setHost(InetAddress.getLocalHost().getHostName());
            } catch (UnknownHostException e) {
                auditLog.setHost("unknown");
            }
        }

        // Nível padrão
        if (auditLog.getNivel() == null) {
            auditLog.setNivel("INFO");
        }
    }

    /**
     * Gera nome do índice com rollover diário
     *
     * <p>Formato: {@code audit-logs-2025.11.30}
     *
     * <p>Benefícios do rollover diário:
     * <ul>
     *   <li>Facilita purge de dados antigos (deleta índice inteiro)</li>
     *   <li>Otimiza queries por período (apenas índices relevantes)</li>
     *   <li>Distribui carga de indexação</li>
     *   <li>Permite diferentes configurações por período</li>
     * </ul>
     *
     * @param timestamp Data/hora do evento
     * @return Nome do índice (ex: audit-logs-2025.11.30)
     */
    private String gerarNomeIndice(LocalDateTime timestamp) {
        String dateSuffix = timestamp.toLocalDate().format(INDEX_DATE_FORMATTER);
        return indexPrefix + "-" + dateSuffix;
    }

    /**
     * Cria builder pré-configurado para eventos de auditoria
     *
     * <p>Helper method para facilitar criação de documentos de auditoria
     * com campos comuns já preenchidos.
     *
     * <p>Exemplo de uso:
     * <pre>{@code
     * AuditLogDocument log = auditRepository.builder()
     *     .evento("PROCESSAMENTO_CONCLUIDO")
     *     .cpf("12345678901")
     *     .idColeta("COL-123")
     *     .detalhes("Status: VALIDA")
     *     .build();
     * auditRepository.registrar(log);
     * }</pre>
     *
     * @return Builder pré-configurado
     */
    public AuditLogDocument.AuditLogDocumentBuilder builder() {
        return AuditLogDocument.builder()
            .timestamp(LocalDateTime.now())
            .origem(applicationName)
            .versaoApp(appVersion)
            .nivel("INFO");
    }

    /**
     * Registra evento INFO (helper method)
     *
     * @param evento Tipo do evento
     * @param cpf CPF do cidadão
     * @param idColeta ID da coleta
     * @param detalhes Detalhes do evento
     * @return ID do documento criado
     */
    public String registrarInfo(String evento, String cpf, String idColeta, String detalhes) {
        AuditLogDocument log = builder()
            .evento(evento)
            .cpf(cpf)
            .idColeta(idColeta)
            .detalhes(detalhes)
            .nivel("INFO")
            .build();

        return registrar(log);
    }

    /**
     * Registra evento WARN (helper method)
     *
     * @param evento Tipo do evento
     * @param cpf CPF do cidadão
     * @param idColeta ID da coleta
     * @param detalhes Detalhes do evento
     * @return ID do documento criado
     */
    public String registrarWarn(String evento, String cpf, String idColeta, String detalhes) {
        AuditLogDocument log = builder()
            .evento(evento)
            .cpf(cpf)
            .idColeta(idColeta)
            .detalhes(detalhes)
            .nivel("WARN")
            .build();

        return registrar(log);
    }

    /**
     * Registra evento ERROR (helper method)
     *
     * @param evento Tipo do evento
     * @param cpf CPF do cidadão
     * @param idColeta ID da coleta
     * @param detalhes Detalhes do evento
     * @return ID do documento criado
     */
    public String registrarErro(String evento, String cpf, String idColeta, String detalhes) {
        AuditLogDocument log = builder()
            .evento(evento)
            .cpf(cpf)
            .idColeta(idColeta)
            .detalhes(detalhes)
            .nivel("ERROR")
            .build();

        return registrar(log);
    }

    /**
     * Registra evento com medição de duração (para métricas de performance)
     *
     * @param evento Tipo do evento
     * @param cpf CPF do cidadão
     * @param idColeta ID da coleta
     * @param duracaoMs Duração da operação em ms
     * @param detalhes Detalhes do evento
     * @return ID do documento criado
     */
    public String registrarComDuracao(
            String evento,
            String cpf,
            String idColeta,
            long duracaoMs,
            String detalhes) {

        AuditLogDocument log = builder()
            .evento(evento)
            .cpf(cpf)
            .idColeta(idColeta)
            .detalhes(detalhes)
            .duracaoMs(duracaoMs)
            .nivel("INFO")
            .build();

        return registrar(log);
    }
}