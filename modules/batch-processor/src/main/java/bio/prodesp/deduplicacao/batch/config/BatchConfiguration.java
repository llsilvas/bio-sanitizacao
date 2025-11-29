package bio.prodesp.deduplicacao.batch.config;

import bio.prodesp.deduplicacao.commons.model.dto.ColetaRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

import bio.prodesp.deduplicacao.batch.listener.JobCompletionListener;
import bio.prodesp.deduplicacao.batch.listener.StepExecutionListener;
import bio.prodesp.deduplicacao.batch.partition.BiometricRecordPartitioner;
import bio.prodesp.deduplicacao.batch.processor.ColetaRecordProcessor;
import bio.prodesp.deduplicacao.batch.reader.OpenSearchItemReader;
import bio.prodesp.deduplicacao.batch.service.DistributedLockService;
import bio.prodesp.deduplicacao.batch.service.OpenSearchService;
import org.springframework.batch.core.configuration.annotation.StepScope;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class BatchConfiguration {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final BiometricRecordPartitioner partitioner;
    private final ColetaRecordProcessor coletaRecordProcessor;
    private final DistributedLockService lockService;

    @Value("${batch.partition.grid-size:10}")
    private int gridSize;

    @Value("${batch.chunk-size:100}")
    private int chunkSize;

    /**
     * Thread pool para execução paralela das partições
     */
    @Bean
    public TaskExecutor batchTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(gridSize);
        executor.setMaxPoolSize(gridSize * 2);
        executor.setQueueCapacity(gridSize * 5);
        executor.setThreadNamePrefix("batch-partition-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * Job principal de processamento de registros biométricos
     */
    @Bean
    public Job biometricDeduplicationJob(
            Step masterStep,
            JobCompletionListener jobCompletionListener) {

        return new JobBuilder("biometricDeduplicationJob", jobRepository)
                .start(masterStep)
                .listener(jobCompletionListener)
                .build();
    }

    /**
     * Master step que coordena as partições
     */
    @Bean
    public Step masterStep(
            TaskExecutorPartitionHandler partitionHandler,
            Step workerStep) {

        return new StepBuilder("masterStep", jobRepository)
                .partitioner("workerStep", partitioner)
                .partitionHandler(partitionHandler)
                .step(workerStep)
                .build();
    }

    /**
     * Partition handler para distribuir o trabalho entre threads
     */
    @Bean
    public TaskExecutorPartitionHandler partitionHandler(
            Step workerStep,
            TaskExecutor batchTaskExecutor) {

        TaskExecutorPartitionHandler handler = new TaskExecutorPartitionHandler();
        handler.setStep(workerStep);
        handler.setTaskExecutor(batchTaskExecutor);
        handler.setGridSize(gridSize);
        return handler;
    }

    /**
     * Worker step que processa cada partição
     */
    @Bean
    public Step workerStep(
            ItemReader<ColetaRecord> coletaRecordItemReader,
            ItemWriter<ColetaRecord> coletaRecordItemWriter,
            StepExecutionListener stepListener) {

        return new StepBuilder("workerStep", jobRepository)
                .<ColetaRecord, ColetaRecord>chunk(chunkSize, transactionManager)
                .reader(coletaRecordItemReader)
                .processor(coletaRecordProcessor)
                .writer(coletaRecordItemWriter)
                .listener(stepListener)
                .faultTolerant()
                .skipLimit(100)
                .skip(Exception.class)
                .retryLimit(3)
                .retry(Exception.class)
                .build();
    }

    /**
     * ItemReader com escopo de step - cada partição cria sua própria instância
     *
     * @StepScope garante que um novo reader é criado para cada partição
     * Os valores #{stepExecutionContext[...]} são injetados pelo partitioner
     */
    @Bean
    @StepScope
    public ItemReader<ColetaRecord> coletaRecordItemReader(
            OpenSearchService openSearchService,
            @Value("#{stepExecutionContext['partitionNumber']}") Integer partitionNumber,
            @Value("#{stepExecutionContext['totalPartitions']}") Integer totalPartitions) {

        log.info("Creating OpenSearchItemReader for partition {} of {}",
                partitionNumber, totalPartitions);

        return new OpenSearchItemReader(openSearchService, partitionNumber, totalPartitions);
    }

    /**
     * Configuração do RestTemplate para chamar o serviço de deduplicação
     */
    @Bean
    public org.springframework.web.client.RestTemplate restTemplate() {
        return new org.springframework.web.client.RestTemplate();
    }
}