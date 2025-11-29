package br.gov.sp.prodesp.deduplicacao.batch.writer;

import br.gov.sp.prodesp.deduplicacao.model.dto.BiometricDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ItemWriter para atualizar documentos processados no OpenSearch
 */
@Slf4j
@RequiredArgsConstructor
public class OpenSearchItemWriter implements ItemWriter<BiometricDocument> {

    private final OpenSearchClient openSearchClient;
    private final String indexName;
    private final int partitionNumber;

    @Override
    public void write(Chunk<? extends BiometricDocument> chunk) throws Exception {
        if (chunk.isEmpty()) {
            return;
        }

        List<? extends BiometricDocument> documents = chunk.getItems();
        log.info("Partition {}: writing {} documents to OpenSearch",
                partitionNumber, documents.size());

        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

            for (BiometricDocument doc : documents) {
                // Cria um mapa com os campos a serem atualizados
                Map<String, Object> updateFields = new HashMap<>();
                updateFields.put("processed", doc.getProcessed());
                updateFields.put("processed_at", doc.getProcessedAt());
                updateFields.put("record_status", doc.getRecordStatus());
                updateFields.put("updated_at", doc.getUpdatedAt());

                if (doc.getDuplicateOf() != null) {
                    updateFields.put("duplicate_of", doc.getDuplicateOf());
                }
                if (doc.getMatchScore() != null) {
                    updateFields.put("match_score", doc.getMatchScore());
                }

                // Cria operação de update com partial document
                bulkBuilder.operations(op -> op
                        .update(u -> u
                                .index(indexName)
                                .id(doc.getId())
                                .document(updateFields)
                        )
                );
            }

            // Executa o bulk update
            BulkResponse response = openSearchClient.bulk(bulkBuilder.build());

            // Verifica erros
            if (response.errors()) {
                handleBulkErrors(response, documents);
            } else {
                log.info("Partition {}: successfully updated {} documents",
                        partitionNumber, documents.size());
            }

        } catch (IOException e) {
            log.error("Partition {}: failed to write documents to OpenSearch: {}",
                    partitionNumber, e.getMessage(), e);
            throw new RuntimeException("Failed to update documents in OpenSearch", e);
        }
    }

    /**
     * Trata erros do bulk update
     */
    private void handleBulkErrors(
            BulkResponse response,
            List<? extends BiometricDocument> documents) {

        List<String> failedIds = new ArrayList<>();
        int errorCount = 0;

        for (BulkResponseItem item : response.items()) {
            if (item.error() != null) {
                errorCount++;
                failedIds.add(item.id());
                log.error("Partition {}: failed to update document {}: {} - {}",
                        partitionNumber,
                        item.id(),
                        item.error().type(),
                        item.error().reason());
            }
        }

        log.error("Partition {}: {} out of {} documents failed to update. Failed IDs: {}",
                partitionNumber,
                errorCount,
                documents.size(),
                failedIds);

        throw new RuntimeException(String.format(
                "Bulk update failed for %d documents in partition %d",
                errorCount, partitionNumber));
    }
}