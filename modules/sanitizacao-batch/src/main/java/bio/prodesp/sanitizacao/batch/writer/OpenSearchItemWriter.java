package bio.prodesp.deduplicacao.batch.writer;

import bio.prodesp.deduplicacao.commons.model.dto.ColetaRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ItemWriter para atualizar coletas processadas no OpenSearch
 * Persiste os resultados do processamento (statusValidacao, abisEncounterId, matchScore)
 *
 * IMPORTANTE: Esta classe NÃO é um Spring Bean. Cada partição cria uma instância própria.
 */
@Slf4j
@RequiredArgsConstructor
public class OpenSearchItemWriter implements ItemWriter<ColetaRecord> {

    private final OpenSearchClient openSearchClient;
    private final String indexName;
    private final int partitionNumber;

    @Override
    public void write(Chunk<? extends ColetaRecord> chunk) throws Exception {
        if (chunk.isEmpty()) {
            return;
        }

        List<? extends ColetaRecord> records = chunk.getItems();
        log.info("Partition {}: writing {} coletas to OpenSearch",
                partitionNumber, records.size());

        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

            for (ColetaRecord record : records) {
                // Cria um mapa com os campos a serem atualizados
                Map<String, Object> updateFields = new HashMap<>();

                // Status de validação
                if (record.getStatusValidacao() != null) {
                    updateFields.put("statusValidacao", record.getStatusValidacao().name());
                }

                // ID do encounter no ABIS
                if (record.getAbisEncounterId() != null) {
                    updateFields.put("abisEncounterId", record.getAbisEncounterId());
                }

                // Score do match biométrico
                if (record.getMatchScore() != null) {
                    updateFields.put("matchScore", record.getMatchScore());
                }

                // Motivo inconclusivo
                if (record.getMotivoInconclusivo() != null) {
                    updateFields.put("motivoInconclusivo", record.getMotivoInconclusivo());
                }

                // Motivo rejeição
                if (record.getMotivoRejeicao() != null) {
                    updateFields.put("motivoRejeicao", record.getMotivoRejeicao());
                }

                // Mensagem de erro
                if (record.getMensagemErro() != null) {
                    updateFields.put("mensagemErro", record.getMensagemErro());
                }

                // Flag de processado
                updateFields.put("processado", record.isProcessado());

                // Timestamp de processamento
                updateFields.put("dataProcessamento", LocalDateTime.now().toString());

                // Cria operação de update com partial document
                bulkBuilder.operations(op -> op
                        .update(u -> u
                                .index(indexName)
                                .id(record.getIdColeta())
                                .document(updateFields)
                        )
                );
            }

            // Executa o bulk update
            BulkResponse response = openSearchClient.bulk(bulkBuilder.build());

            // Verifica erros
            if (response.errors()) {
                handleBulkErrors(response, records);
            } else {
                log.info("Partition {}: successfully updated {} coletas",
                        partitionNumber, records.size());
            }

        } catch (IOException e) {
            log.error("Partition {}: failed to write coletas to OpenSearch: {}",
                    partitionNumber, e.getMessage(), e);
            throw new RuntimeException("Failed to update coletas in OpenSearch", e);
        }
    }

    /**
     * Trata erros do bulk update
     */
    private void handleBulkErrors(
            BulkResponse response,
            List<? extends ColetaRecord> records) {

        List<String> failedIds = new ArrayList<>();
        int errorCount = 0;

        for (BulkResponseItem item : response.items()) {
            if (item.error() != null) {
                errorCount++;
                failedIds.add(item.id());
                log.error("Partition {}: failed to update coleta {}: {} - {}",
                        partitionNumber,
                        item.id(),
                        item.error().type(),
                        item.error().reason());
            }
        }

        log.error("Partition {}: {} out of {} coletas failed to update. Failed IDs: {}",
                partitionNumber,
                errorCount,
                records.size(),
                failedIds);

        throw new RuntimeException(String.format(
                "Bulk update failed for %d coletas in partition %d",
                errorCount, partitionNumber));
    }
}