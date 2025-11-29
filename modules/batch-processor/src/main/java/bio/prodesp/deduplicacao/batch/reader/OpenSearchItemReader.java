package bio.prodesp.deduplicacao.batch.reader;

import bio.prodesp.deduplicacao.commons.model.domain.ColetaMetadata;
import bio.prodesp.deduplicacao.commons.model.dto.ColetaRecord;
import bio.prodesp.deduplicacao.batch.service.OpenSearchService;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.core.ScrollResponse;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.batch.item.ItemReader;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

/**
 * ItemReader customizado para ler coletas do OpenSearch usando scroll API
 * Retorna ColetaRecord que encapsula ColetaMetadata
 *
 * IMPORTANTE: Esta classe NÃO é um Spring Bean. Cada partição cria uma instância própria.
 */
@Slf4j
public class OpenSearchItemReader implements ItemReader<ColetaRecord> {

    private final OpenSearchService openSearchService;
    private final int partitionNumber;
    private final int totalPartitions;

    private String scrollId;
    private Queue<ColetaRecord> documentQueue = new LinkedList<>();
    private boolean exhausted = false;
    private long totalRead = 0;

    public OpenSearchItemReader(OpenSearchService openSearchService, int partitionNumber, int totalPartitions) {
        this.openSearchService = openSearchService;
        this.partitionNumber = partitionNumber;
        this.totalPartitions = totalPartitions;
    }

    @Override
    public ColetaRecord read() throws Exception {
        // Primeira leitura - inicia o scroll
        if (scrollId == null && !exhausted) {
            initializeScroll();
        }

        // Se a fila está vazia e ainda há dados, busca mais
        if (documentQueue.isEmpty() && !exhausted) {
            fetchNextBatch();
        }

        // Retorna próximo documento da fila
        ColetaRecord record = documentQueue.poll();

        if (record != null) {
            totalRead++;
            if (totalRead % 1000 == 0) {
                log.info("Partition {}: read {} coletas so far", partitionNumber, totalRead);
            }
        } else {
            // Fim da leitura - limpa o scroll
            cleanup();
        }

        return record;
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
            log.info("No coletas found for partition {}", partitionNumber);
            exhausted = true;
            return;
        }

        // Adiciona coletas à fila como ColetaRecord
        for (Hit<ColetaMetadata> hit : response.hits().hits()) {
            if (hit.source() != null) {
                ColetaRecord record = ColetaRecord.from(hit.source(), partitionNumber);
                documentQueue.offer(record);
            }
        }

        log.info("Partition {}: initialized scroll with {} coletas",
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
            log.info("Partition {}: no more coletas available", partitionNumber);
            exhausted = true;
            return;
        }

        // Adiciona novas coletas à fila como ColetaRecord
        for (Hit<ColetaMetadata> hit : response.hits().hits()) {
            if (hit.source() != null) {
                ColetaRecord record = ColetaRecord.from(hit.source(), partitionNumber);
                documentQueue.offer(record);
            }
        }

        log.debug("Partition {}: fetched {} more coletas",
                partitionNumber, response.hits().hits().size());
    }

    /**
     * Limpa recursos ao final da leitura
     */
    private void cleanup() {
        if (scrollId != null) {
            log.info("Partition {}: cleaning up scroll. Total coletas read: {}",
                    partitionNumber, totalRead);
            try {
                openSearchService.clearScroll(scrollId);
            } catch (Exception e) {
                log.warn("Partition {}: failed to clear scroll ID {} - {}",
                        partitionNumber, scrollId, e.getMessage());
            } finally {
                scrollId = null;
            }
        }
    }
}
