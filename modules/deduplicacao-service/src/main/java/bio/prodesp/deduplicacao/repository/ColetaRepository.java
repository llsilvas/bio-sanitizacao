package bio.prodesp.deduplicacao.repository;

import bio.prodesp.deduplicacao.commons.model.domain.ColetaMetadata;
import bio.prodesp.deduplicacao.commons.model.enums.StatusValidacao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch.core.*;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Repository para operações de persistência de coletas biométricas no OpenSearch
 *
 * <p>Responsável por atualizar documentos de coletas após processamento de deduplicação.
 * Utiliza partial update para otimizar performance, atualizando apenas campos modificados.
 *
 * <p><b>Importante:</b> Este repository NÃO utiliza JPA/SQL. O OpenSearch é a fonte de verdade
 * para dados de coletas biométricas.
 *
 * @author Bio Sanitização Team
 * @since 1.0.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ColetaRepository {

    private final OpenSearchClient openSearchClient;

    @Value("${opensearch.index:biometric-data}")
    private String indexName;

    /**
     * Atualiza status de processamento da coleta com informações do resultado de deduplicação
     *
     * <p>Utiliza partial update (apenas campos informados são atualizados no documento).
     * Possui retry automático em caso de conflito de versão (3 tentativas).
     *
     * @param idColeta ID único da coleta
     * @param statusValidacao Status após processamento (VALIDA, INCONCLUSIVA, INVALIDA, etc)
     * @param abisRecordId ID do encounter no ABIS (nullable)
     * @param motivoInconclusivo Motivo se status = INCONCLUSIVA (nullable)
     * @param motivoRejeicao Motivo se status = INVALIDA (nullable)
     * @param matchScore Score de matching OSIA (nullable)
     * @return true se atualizado com sucesso, false caso contrário
     */
    public boolean atualizarStatusProcessamento(
            String idColeta,
            StatusValidacao statusValidacao,
            String abisRecordId,
            String motivoInconclusivo,
            String motivoRejeicao,
            Double matchScore) {

        try {
            // Construir documento de atualização parcial
            Map<String, Object> updateDoc = new HashMap<>();
            updateDoc.put("processado", true);
            updateDoc.put("status", statusValidacao.name()); // Mapeado para campo 'status' no OpenSearch
            updateDoc.put("dataProcessamento", LocalDateTime.now().toString());

            // Campos opcionais
            if (abisRecordId != null) {
                updateDoc.put("abisRecordId", abisRecordId);
            }
            if (motivoInconclusivo != null) {
                updateDoc.put("motivoInconclusivo", motivoInconclusivo);
            }
            if (motivoRejeicao != null) {
                updateDoc.put("motivoRejeicao", motivoRejeicao);
            }
            if (matchScore != null) {
                updateDoc.put("matchScore", matchScore);
            }

            // Regras de negócio para validacaoPendente
            if (statusValidacao == StatusValidacao.INCONCLUSIVA) {
                updateDoc.put("validacaoPendente", true);
            } else if (statusValidacao == StatusValidacao.VALIDA) {
                updateDoc.put("validacaoPendente", false);
            }

            // Executar partial update
            UpdateResponse<Map> response = openSearchClient.update(
                UpdateRequest.of(u -> u
                    .index(indexName)
                    .id(idColeta)
                    .doc(updateDoc)
                    .refresh(Refresh.False) // Não forçar refresh (melhor performance)
                    .retryOnConflict(3) // Retry em caso de conflito de versão
                ),
                Map.class
            );

            boolean success = response.result() == Result.Updated;

            if (success) {
                log.info("Coleta atualizada no OpenSearch - ID: {}, Status: {}, Result: {}",
                        idColeta, statusValidacao, response.result());
            } else {
                log.warn("Falha ao atualizar coleta no OpenSearch - ID: {}, Result: {}",
                        idColeta, response.result());
            }

            return success;

        } catch (IOException e) {
            log.error("Erro ao atualizar coleta no OpenSearch - ID: {}", idColeta, e);
            return false;
        }
    }

    /**
     * Marca coleta como processada com erro reprocessável
     *
     * <p>Utilizado quando ocorre erro temporário (ex: timeout OSIA, indisponibilidade ABIS).
     * A coleta ficará com processado=false para permitir reprocessamento futuro.
     *
     * @param idColeta ID único da coleta
     * @param mensagemErro Mensagem de erro detalhada
     * @return true se atualizado com sucesso
     */
    public boolean marcarErroReprocessavel(String idColeta, String mensagemErro) {
        try {
            Map<String, Object> updateDoc = Map.of(
                "processado", false, // Permite reprocessamento
                "status", StatusValidacao.ERRO_REPROCESSAVEL.name(),
                "mensagemErro", mensagemErro != null ? mensagemErro : "Erro desconhecido",
                "dataUltimaFalha", LocalDateTime.now().toString()
            );

            UpdateResponse<Map> response = openSearchClient.update(
                UpdateRequest.of(u -> u
                    .index(indexName)
                    .id(idColeta)
                    .doc(updateDoc)
                    .retryOnConflict(3)
                ),
                Map.class
            );

            log.warn("Coleta marcada para reprocessamento - ID: {}, Erro: {}", idColeta, mensagemErro);
            return response.result() == Result.Updated;

        } catch (IOException e) {
            log.error("Erro ao marcar coleta para reprocessamento - ID: {}", idColeta, e);
            return false;
        }
    }

    /**
     * Marca coleta como inválida (erro não recuperável)
     *
     * <p>Utilizado para erros permanentes (ex: CPF inválido, violação de unicidade ABIS).
     * A coleta NÃO será reprocessada automaticamente.
     *
     * @param idColeta ID único da coleta
     * @param motivoRejeicao Motivo da invalidação
     * @return true se atualizado com sucesso
     */
    public boolean marcarInvalida(String idColeta, String motivoRejeicao) {
        try {
            Map<String, Object> updateDoc = Map.of(
                "processado", true, // Processamento concluído (mesmo com erro)
                "status", StatusValidacao.INVALIDA.name(),
                "motivoRejeicao", motivoRejeicao,
                "dataProcessamento", LocalDateTime.now().toString()
            );

            UpdateResponse<Map> response = openSearchClient.update(
                UpdateRequest.of(u -> u
                    .index(indexName)
                    .id(idColeta)
                    .doc(updateDoc)
                    .retryOnConflict(3)
                ),
                Map.class
            );

            log.warn("Coleta marcada como INVALIDA - ID: {}, Motivo: {}", idColeta, motivoRejeicao);
            return response.result() == Result.Updated;

        } catch (IOException e) {
            log.error("Erro ao marcar coleta como inválida - ID: {}", idColeta, e);
            return false;
        }
    }

    /**
     * Busca coleta por ID (para verificação de idempotência avançada)
     *
     * <p>Retorna o documento completo do OpenSearch como Map.
     * Usado pelo IdempotenciaService para fallback quando Redis cache falha.
     *
     * @param idColeta ID único da coleta
     * @return Optional com Map contendo os campos do documento se encontrado
     */
    public Optional<Map<String, Object>> buscarPorId(String idColeta) {
        try {
            GetResponse<Map> response = openSearchClient.get(
                GetRequest.of(g -> g
                    .index(indexName)
                    .id(idColeta)
                ),
                Map.class
            );

            if (response.found()) {
                log.debug("Coleta encontrada no OpenSearch - ID: {}", idColeta);
                @SuppressWarnings("unchecked")
                Map<String, Object> source = (Map<String, Object>) response.source();
                return Optional.ofNullable(source);
            }

            log.debug("Coleta não encontrada no OpenSearch - ID: {}", idColeta);
            return Optional.empty();

        } catch (IOException e) {
            log.error("Erro ao buscar coleta no OpenSearch - ID: {}", idColeta, e);
            return Optional.empty();
        }
    }

    /**
     * Busca coleta por CPF e ID (validação adicional)
     *
     * <p>Utiliza query composta para garantir que CPF e ID correspondem ao mesmo documento.
     * Útil para validações de integridade antes do processamento.
     *
     * @param cpf CPF do cidadão
     * @param idColeta ID único da coleta
     * @return Optional com ColetaMetadata se encontrado
     */
    public Optional<ColetaMetadata> buscarPorCpfEId(String cpf, String idColeta) {
        try {
            SearchResponse<ColetaMetadata> response = openSearchClient.search(
                SearchRequest.of(s -> s
                    .index(indexName)
                    .query(q -> q
                        .bool(b -> b
                            .must(m -> m.term(t -> t.field("cpf").value(FieldValue.of(cpf))))
                            .must(m -> m.term(t -> t.field("idColeta").value(FieldValue.of(idColeta))))
                        )
                    )
                    .size(1)
                ),
                ColetaMetadata.class
            );

            if (!response.hits().hits().isEmpty()) {
                Hit<ColetaMetadata> hit = response.hits().hits().get(0);
                log.debug("Coleta encontrada por CPF e ID - CPF: {}, ID: {}", cpf, idColeta);
                return Optional.ofNullable(hit.source());
            }

            log.debug("Coleta não encontrada por CPF e ID - CPF: {}, ID: {}", cpf, idColeta);
            return Optional.empty();

        } catch (IOException e) {
            log.error("Erro ao buscar coleta por CPF e ID - CPF: {}, ID: {}", cpf, idColeta, e);
            return Optional.empty();
        }
    }

    /**
     * Verifica se coleta já foi processada (consulta simples para idempotência)
     *
     * <p>Retorna true se documento existe E campo 'processado' = true.
     * Mais eficiente que buscarPorId() quando só precisa verificar status.
     *
     * @param idColeta ID único da coleta
     * @return true se já processada
     */
    public boolean jaFoiProcessada(String idColeta) {
        try {
            SearchResponse<Map> response = openSearchClient.search(
                SearchRequest.of(s -> s
                    .index(indexName)
                    .query(q -> q
                        .bool(b -> b
                            .must(m -> m.term(t -> t.field("idColeta").value(FieldValue.of(idColeta))))
                            .must(m -> m.term(t -> t.field("processado").value(FieldValue.of(true))))
                        )
                    )
                    .size(0) // Não retorna documentos, apenas hit count
                    .trackTotalHits(t -> t.enabled(true))
                ),
                Map.class
            );

            boolean processada = response.hits().total() != null && response.hits().total().value() > 0;

            if (processada) {
                log.debug("Coleta já foi processada - ID: {}", idColeta);
            }

            return processada;

        } catch (IOException e) {
            log.error("Erro ao verificar se coleta foi processada - ID: {}", idColeta, e);
            return false; // Em caso de erro, permite reprocessamento (fail-open)
        }
    }

    /**
     * Atualiza ID do encounter ABIS no documento da coleta
     *
     * <p>Usado após cadastro bem-sucedido no ABIS para registrar o encounter ID.
     * Permite rastreamento e auditoria posterior.
     *
     * @param idColeta ID único da coleta
     * @param abisEncounterId ID do encounter no ABIS
     * @return true se atualizado com sucesso
     */
    public boolean atualizarAbisEncounterId(String idColeta, String abisEncounterId) {
        try {
            Map<String, Object> updateDoc = Map.of(
                "abisRecordId", abisEncounterId,
                "dataUltimaAtualizacao", LocalDateTime.now().toString()
            );

            UpdateResponse<Map> response = openSearchClient.update(
                UpdateRequest.of(u -> u
                    .index(indexName)
                    .id(idColeta)
                    .doc(updateDoc)
                    .retryOnConflict(3)
                ),
                Map.class
            );

            log.info("ABIS Encounter ID atualizado - Coleta ID: {}, Encounter ID: {}",
                    idColeta, abisEncounterId);

            return response.result() == Result.Updated;

        } catch (IOException e) {
            log.error("Erro ao atualizar ABIS Encounter ID - Coleta ID: {}", idColeta, e);
            return false;
        }
    }
}