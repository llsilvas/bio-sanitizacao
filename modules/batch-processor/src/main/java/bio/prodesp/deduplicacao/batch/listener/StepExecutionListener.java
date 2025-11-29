package br.gov.sp.prodesp.deduplicacao.batch.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StepExecutionListener implements org.springframework.batch.core.StepExecutionListener {

    @Override
    public void beforeStep(org.springframework.batch.core.StepExecution stepExecution) {
        Integer partitionNumber = stepExecution.getExecutionContext().getInt("partitionNumber", -1);
        log.info("Starting step '{}' - Partition: {}",
                stepExecution.getStepName(),
                partitionNumber);
    }

    @Override
    public ExitStatus afterStep(org.springframework.batch.core.StepExecution stepExecution) {
        Integer partitionNumber = stepExecution.getExecutionContext().getInt("partitionNumber", -1);

        log.info("Finished step '{}' - Partition: {} | Read: {} | Written: {} | Skipped: {}",
                stepExecution.getStepName(),
                partitionNumber,
                stepExecution.getReadCount(),
                stepExecution.getWriteCount(),
                stepExecution.getSkipCount());

        if (!stepExecution.getFailureExceptions().isEmpty()) {
            log.error("Step completed with {} failures in partition {}",
                    stepExecution.getFailureExceptions().size(),
                    partitionNumber);

            stepExecution.getFailureExceptions().forEach(ex ->
                    log.error("Step failure: {}", ex.getMessage(), ex));
        }

        return stepExecution.getExitStatus();
    }
}