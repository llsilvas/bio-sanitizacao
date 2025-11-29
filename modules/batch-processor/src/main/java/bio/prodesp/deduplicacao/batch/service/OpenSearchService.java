package br.gov.sp.prodesp.deduplicacao.batch.service;

import br.gov.sp.prodesp.deduplicacao.model.dto.BiometricDocument;
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

    @Value("${opensearch.index:biometric-data}")
    private String indexName;

    @Value("${opensearch.scroll.timeout:5m}")
    private String scrollTimeout;

    @Value("${opensearch.scroll.size:1000}")
    private int scrollSize;

    /**
     * Busca documentos não processados de uma partição específica usando scroll
     *
     * @param partitionNumber número da partição
     * @param totalPartitions total de partições
     * @return response com scroll ID e documentos
     */
    public SearchResponse<BiometricDocument> searchUnprocessedByPartition(
            int partitionNumber,
            int totalPartitions) throws IOException {

        log.info("Searching OpenSearch for partition {} of {}", partitionNumber, totalPartitions);

        // Query para filtrar por partição e registros não processados
        Query processedQuery = TermQuery.of(t -> t
                .field("processed")
                .value(FieldValue.of(false))
        )._toQuery();

        Query partitionQuery = TermQuery.of(t -> t
                .field("partition_key")
                .value(FieldValue.of(partitionNumber))
        )._toQuery();

        BoolQuery boolQuery = BoolQuery.of(b -> b
                .must(processedQuery)
                .must(partitionQuery)
        );

        SearchRequest searchRequest = SearchRequest.of(s -> s
                .index(indexName)
                .query(boolQuery._toQuery())
                .size(scrollSize)
                .scroll(t -> t.time(scrollTimeout))
                .sort(so -> so.field(f -> f.field("document_number").order(SortOrder.Asc)))
                .sort(so -> so.field(f -> f.field("_id").order(SortOrder.Asc)))
        );

        SearchResponse<BiometricDocument> response = openSearchClient.search(
                searchRequest,
                BiometricDocument.class
        );

        log.info("Found {} documents in partition {}, scroll ID: {}",
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
    public ScrollResponse<BiometricDocument> scroll(String scrollId) throws IOException {
        log.debug("Scrolling with ID: {}", scrollId);

        ScrollRequest scrollRequest = ScrollRequest.of(s -> s
                .scrollId(scrollId)
                .scroll(t -> t.time(scrollTimeout))
        );

        return openSearchClient.scroll(scrollRequest, BiometricDocument.class);
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
     * Busca todos os documentos de uma partição (sem paginação, para testes)
     *
     * @param partitionNumber número da partição
     * @param totalPartitions total de partições
     * @param size número máximo de resultados
     * @return lista de documentos
     */
    public List<BiometricDocument> findUnprocessedByPartition(
            int partitionNumber,
            int totalPartitions,
            int size) throws IOException {

        SearchResponse<BiometricDocument> response = searchUnprocessedByPartition(
                partitionNumber,
                totalPartitions
        );

        List<BiometricDocument> documents = new ArrayList<>();
        for (Hit<BiometricDocument> hit : response.hits().hits()) {
            documents.add(hit.source());
        }

        log.info("Retrieved {} documents for partition {}", documents.size(), partitionNumber);
        return documents;
    }

    /**
     * Conta documentos não processados em uma partição
     *
     * @param partitionNumber número da partição
     * @param totalPartitions total de partições
     * @return contagem de documentos
     */
    public long countUnprocessedByPartition(
            int partitionNumber,
            int totalPartitions) throws IOException {

        Query processedQuery = TermQuery.of(t -> t
                .field("processed")
                .value(FieldValue.of(false))
        )._toQuery();

        Query partitionQuery = TermQuery.of(t -> t
                .field("partition_key")
                .value(FieldValue.of(partitionNumber))
        )._toQuery();

        BoolQuery boolQuery = BoolQuery.of(b -> b
                .must(processedQuery)
                .must(partitionQuery)
        );

        var countResponse = openSearchClient.count(c -> c
                .index(indexName)
                .query(boolQuery._toQuery())
        );

        long count = countResponse.count();
        log.info("Partition {} has {} unprocessed documents", partitionNumber, count);
        return count;
    }
}