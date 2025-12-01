package bio.prodesp.deduplicacao.batch.service;

import bio.prodesp.deduplicacao.commons.model.domain.ColetaMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.client.opensearch.core.ScrollRequest;
import org.opensearch.client.opensearch.core.ScrollResponse;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenSearchService {

    private final OpenSearchClient openSearchClient;

    @Value("${opensearch.index:coletas}")
    private String indexName;

    @Value("${opensearch.scroll.timeout:5m}")
    private String scrollTimeout;

    @Value("${opensearch.scroll.size:1000}")
    private int scrollSize;

    /**
     * Busca coletas não processadas de uma partição específica usando scroll
     *
     * @param partitionNumber número da partição (baseado em hash do CPF)
     * @param totalPartitions total de partições
     * @return response com scroll ID e coletas
     */
    public SearchResponse<ColetaMetadata> searchUnprocessedByPartition(
            int partitionNumber,
            int totalPartitions) throws IOException {

        log.info("Searching OpenSearch for partition {} of {} (index: {})",
                partitionNumber, totalPartitions, indexName);

        // ===== CONSTRUÇÃO DA QUERY =====

        // Filtro 1: Coletas não processadas
        // Busca documentos onde processado = false OU processado não existe
        Query processadoQuery = BoolQuery.of(b -> b
                .should(TermQuery.of(t -> t
                        .field("processado")
                        .value(FieldValue.of(false))
                )._toQuery())
                .should(q -> q.bool(bq -> bq
                        .mustNot(mn -> mn.exists(e -> e.field("processado")))
                ))
                .minimumShouldMatch("1")
        )._toQuery();

        // Filtro 2: Validação pendente = false (RN003)
        Query validacaoPendenteQuery = TermQuery.of(t -> t
                .field("validacaoPendente")
                .value(FieldValue.of(false))
        )._toQuery();

        // Filtro 3: Partição baseada no CPF (hash % totalPartitions)
        Query partitionQuery = TermQuery.of(t -> t
                .field("partition_key")
                .value(FieldValue.of(partitionNumber))
        )._toQuery();

        // Bool Query: usa filter (não must) para melhor performance
        // Filter não calcula relevance score, apenas filtra
        BoolQuery boolQuery = BoolQuery.of(b -> b
                .filter(processadoQuery)
                .filter(validacaoPendenteQuery)
                .filter(partitionQuery)
        );

        // ===== CONSTRUÇÃO DO SEARCH REQUEST =====

        SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(indexName)
                .query(boolQuery._toQuery())
                .size(scrollSize)
                .scroll(t -> t.time(scrollTimeout))
                // Sort: garante ordem consistente entre scrolls
                .sort(so -> so.field(f -> f.field("cpf").order(SortOrder.Asc)))
                .sort(so -> so.field(f -> f.field("idColeta").order(SortOrder.Asc)))
                // Source filtering: retorna apenas campos necessários (reduz payload)
                // Comentado por enquanto - retorna todos os campos
                // .source(src -> src
                //     .filter(f -> f
                //         .includes("idColeta", "cpf", "dataNascimento", "dadosBiometricos")
                //         .excludes("auditoria", "metadados")
                //     )
                // )
                // Track total hits: útil para logs e métricas
                .trackTotalHits(t -> t.enabled(true))
        );

        // ===== EXECUÇÃO DA QUERY =====

        long startTime = System.currentTimeMillis();
        SearchResponse<ColetaMetadata> response = openSearchClient.search(
                searchRequest,
                ColetaMetadata.class
        );
        long duration = System.currentTimeMillis() - startTime;

        // ===== LOGGING E MÉTRICAS =====

        log.info("OpenSearch query executed in {}ms for partition {}/{}: " +
                "found {} coletas (total: {}), scroll ID: {}",
                duration,
                partitionNumber,
                totalPartitions,
                response.hits().hits().size(),
                response.hits().total() != null ? response.hits().total().value() : "unknown",
                response.scrollId());

        // Log de warning se não encontrou nada
        if (response.hits().hits().isEmpty()) {
            log.warn("No coletas found for partition {}/{}. " +
                    "Check if partition_key field exists and is correctly populated.",
                    partitionNumber, totalPartitions);
        }

        return response;
    }

    /**
     * Continua a busca usando scroll ID
     *
     * @param scrollId ID do scroll anterior
     * @return próxima página de resultados
     */
    public ScrollResponse<ColetaMetadata> scroll(String scrollId) throws IOException {
        log.debug("Scrolling with ID: {}", scrollId);

        ScrollRequest scrollRequest = ScrollRequest.of(s -> s
                .scrollId(scrollId)
                .scroll(t -> t.time(scrollTimeout))
        );

        return openSearchClient.scroll(scrollRequest, ColetaMetadata.class);
    }

    /**
     * Limpa o contexto de scroll
     *
     * @param scrollId ID do scroll a ser limpo
     */
    public void clearScroll(String scrollId) {
        try {
            openSearchClient.clearScroll(c -> c.scrollId(scrollId));
            log.debug("Scroll cleared: {}", scrollId);
        } catch (IOException e) {
            log.warn("Failed to clear scroll {}: {}", scrollId, e.getMessage());
        }
    }

    /**
     * Busca todas as coletas de uma partição (sem paginação, para testes)
     *
     * @param partitionNumber número da partição
     * @param totalPartitions total de partições
     * @param size número máximo de resultados
     * @return lista de coletas
     */
    public List<ColetaMetadata> findUnprocessedByPartition(
            int partitionNumber,
            int totalPartitions,
            int size) throws IOException {

        SearchResponse<ColetaMetadata> response = searchUnprocessedByPartition(
                partitionNumber,
                totalPartitions
        );

        List<ColetaMetadata> coletas = new ArrayList<>();
        for (Hit<ColetaMetadata> hit : response.hits().hits()) {
            coletas.add(hit.source());
        }

        log.info("Retrieved {} coletas for partition {}", coletas.size(), partitionNumber);
        return coletas;
    }

    /**
     * Conta coletas não processadas em uma partição
     *
     * @param partitionNumber número da partição
     * @param totalPartitions total de partições
     * @return contagem de coletas
     */
    public long countUnprocessedByPartition(
            int partitionNumber,
            int totalPartitions) throws IOException {

        Query validacaoPendenteQuery = TermQuery.of(t -> t
                .field("validacaoPendente")
                .value(FieldValue.of(false))
        )._toQuery();

        Query partitionQuery = TermQuery.of(t -> t
                .field("partition_key")
                .value(FieldValue.of(partitionNumber))
        )._toQuery();

        BoolQuery boolQuery = BoolQuery.of(b -> b
                .must(validacaoPendenteQuery)
                .must(partitionQuery)
        );

        var countResponse = openSearchClient.count(c -> c
                .index(indexName)
                .query(boolQuery._toQuery())
        );

        long count = countResponse.count();
        log.info("Partition {} has {} unprocessed coletas", partitionNumber, count);
        return count;
    }
}
