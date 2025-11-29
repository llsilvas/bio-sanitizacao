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

        log.info("Searching OpenSearch for partition {} of {}", partitionNumber, totalPartitions);

        // Query para filtrar coletas não processadas
        // Critério: validacaoPendente = false (RN003)
        Query validacaoPendenteQuery = TermQuery.of(t -> t
                .field("validacaoPendente")
                .value(FieldValue.of(false))
        )._toQuery();

        // Query para filtrar por partição baseada no CPF
        // partition_key = hash(cpf) % totalPartitions
        Query partitionQuery = TermQuery.of(t -> t
                .field("partition_key")
                .value(FieldValue.of(partitionNumber))
        )._toQuery();

        BoolQuery boolQuery = BoolQuery.of(b -> b
                .must(validacaoPendenteQuery)
                .must(partitionQuery)
        );

        SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(indexName)
                .query(boolQuery._toQuery())
                .size(scrollSize)
                .scroll(t -> t.time(scrollTimeout))
                .sort(so -> so.field(f -> f.field("cpf").order(SortOrder.Asc)))
                .sort(so -> so.field(f -> f.field("idColeta").order(SortOrder.Asc)))
        );

        SearchResponse<ColetaMetadata> response = openSearchClient.search(
                searchRequest,
                ColetaMetadata.class
        );

        log.info("Found {} coletas in partition {}, scroll ID: {}",
                response.hits().hits().size(),
                partitionNumber,
                response.scrollId());

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
