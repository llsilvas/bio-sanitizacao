package bio.prodesp.deduplicacao.batch.reader;

import bio.prodesp.deduplicacao.commons.model.domain;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.core.ScrollResponse;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.batch.item.ItemReader;

import bio.prodesp.deduplicacao.batch.service.OpenSearchService;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

/**
 * ItemReader customizado para ler documentos do OpenSearch usando scroll API
 */
@Slf4j
public class OpenSearchItemReader implements ItemReader<ColetaMetadata> {

    private final OpenSearchService openSearchService;
    private final int partitionNumber;
    private final int totalPartitions;

    private String scrollId;
    private Queue<ColetaMetadata> documentQueue;
    private boolean exhausted = false;
    private long totalRead = 0;

    public OpenSearchItemReader(
            OpenSearchService openSearchService,
            int partitionNumber,
            int totalPartitions) {
        this.openSearchService = openSearchService;
        this.partitionNumber = partitionNumber;
        this.totalPartitions = totalPartitions;
        this.documentQueue = new LinkedList<>();
    }

    @Override
    public ColetaMetadata read() throws Exception {
        // Primeira leitura - inicia o scroll
        if (scrollId == null && !exhausted) {
            initializeScroll();
        }

        // Se a fila está vazia e ainda há dados, busca mais
        if (documentQueue.isEmpty() && !exhausted) {
            fetchNextBatch();
        }

        // Retorna próximo documento da fila
        ColetaMetadata document = documentQueue.poll();

        if (document != null) {
            totalRead++;
            if (totalRead % 1000 == 0) {
                log.info("Partition {}: read {} documents so far", partitionNumber, totalRead);
            }
        } else {
            // Fim da leitura - limpa o scroll
            cleanup();
        }

        return document;
    }

    /**
     * Inicializa o scroll com a primeira busca
     */
    private void initializeScroll() throws IOException {
        log.info("Initializing scroll for partition {} of {}", partitionNumber, totalPartitions);

        SearchResponse<ColetaMetadata> response = openSearchService.searchUnprocessedByPartition(
                partitionNumber,
                totalPartitions
        );

        this.scrollId = response.scrollId();

        if (response.hits().hits().isEmpty()) {
            log.info("No documents found for partition {}", partitionNumber);
            exhausted = true;
            return;
        }

        // Adiciona documentos à fila
        for (Hit<ColetaMetadata> hit : response.hits().hits()) {
            if (hit.source() != null) {
                documentQueue.offer(hit.source());
            }
        }

        log.info("Partition {}: initialized scroll with {} documents",
                partitionNumber, documentQueue.size());
    }

    /**
     * Busca o próximo lote usando scroll
     */
    private void fetchNextBatch() throws IOException {
        if (scrollId == null) {
            exhausted = true;
            return;
        }

        log.debug("Partition {}: fetching next batch with scroll ID {}",
                partitionNumber, scrollId);

        ScrollResponse<ColetaMetadata> response = openSearchService.scroll(scrollId);

        // Atualiza o scroll ID
        this.scrollId = response.scrollId();

        if (response.hits().hits().isEmpty()) {
            log.info("Partition {}: no more documents available", partitionNumber);
            exhausted = true;
            return;
        }

        // Adiciona novos documentos à fila
        for (Hit<ColetaMetadata> hit : response.hits().hits()) {
            if (hit.source() != null) {
                documentQueue.offer(hit.source());
            }
        }

        log.debug("Partition {}: fetched {} more documents",
                partitionNumber, response.hits().hits().size());
    }

    /**
     * Limpa recursos ao final da leitura
     */
    private void cleanup() {
        if (scrollId != null) {
            log.info("Partition {}: cleaning up scroll. Total documents read: {}",
                    partitionNumber, totalRead);
            openSearchService.clearScroll(scrollId);
            scrollId = null;
        }
    }
}