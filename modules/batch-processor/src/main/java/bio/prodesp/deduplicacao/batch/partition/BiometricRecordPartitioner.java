package br.gov.sp.prodesp.deduplicacao.batch.partition;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Partitioner que divide os registros biométricos em partições baseadas no hash do CPF
 * Garante que registros do mesmo CPF sejam sempre processados na mesma partição
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BiometricRecordPartitioner implements Partitioner {

    @Value("${batch.partition.grid-size:10}")
    private int gridSize;

    @Override
    public Map<String, ExecutionContext> partition(int gridSizeOverride) {
        int actualGridSize = gridSizeOverride > 0 ? gridSizeOverride : this.gridSize;

        log.info("Starting CPF-based partitioning with grid size: {}", actualGridSize);

        Map<String, ExecutionContext> partitions = new HashMap<>();

        // Cria uma partição para cada módulo do hash do CPF
        for (int i = 0; i < actualGridSize; i++) {
            ExecutionContext context = new ExecutionContext();

            // Cada partição processará registros onde: hash(CPF) % gridSize == partitionNumber
            context.putInt("partitionNumber", i);
            context.putInt("totalPartitions", actualGridSize);

            String partitionKey = "partition" + i;
            partitions.put(partitionKey, context);

            log.debug("Created partition {}: will process CPFs where hash(CPF) % {} == {}",
                    partitionKey, actualGridSize, i);
        }

        log.info("Created {} CPF-based partitions", partitions.size());
        return partitions;
    }
}