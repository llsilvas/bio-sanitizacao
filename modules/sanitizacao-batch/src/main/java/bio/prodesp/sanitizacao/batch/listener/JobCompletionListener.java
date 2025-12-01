package bio.prodesp.deduplicacao.batch.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import bio.prodesp.deduplicacao.batch.service.DistributedLockService;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobCompletionListener implements JobExecutionListener {

    private final DistributedLockService lockService;
    private static final String JOB_LOCK_KEY = "biometric-deduplication-job";

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("========================================");
        log.info("Job '{}' starting - ID: {}",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getJobId());
        log.info("========================================");

        // Tenta adquirir lock distribuído para evitar execução concorrente
        boolean lockAcquired = lockService.tryLock(JOB_LOCK_KEY, 5, 3600);

        if (!lockAcquired) {
            log.error("Failed to acquire distributed lock. Another job instance may be running.");
            jobExecution.setStatus(BatchStatus.STOPPED);
            throw new IllegalStateException(
                    "Cannot start job: distributed lock already held by another instance");
        }

        log.info("Distributed lock acquired successfully for job execution");
        jobExecution.getExecutionContext().putString("lockKey", JOB_LOCK_KEY);
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String lockKey = jobExecution.getExecutionContext().getString("lockKey");

        if (lockKey != null) {
            lockService.unlock(lockKey);
            log.info("Distributed lock released for job execution");
        }

        LocalDateTime startTime = jobExecution.getStartTime();
        LocalDateTime endTime = jobExecution.getEndTime();

        Duration duration = Duration.between(startTime, endTime);

        log.info("========================================");
        log.info("Job '{}' finished - ID: {}",
                jobExecution.getJobInstance().getJobName(),
                jobExecution.getJobId());
        log.info("Status: {}", jobExecution.getStatus());
        log.info("Duration: {} seconds", duration.getSeconds());
        log.info("Read Count: {}", jobExecution.getStepExecutions().stream()
                .mapToLong(org.springframework.batch.core.StepExecution::getReadCount)
                .sum());
        log.info("Write Count: {}", jobExecution.getStepExecutions().stream()
                .mapToLong(org.springframework.batch.core.StepExecution::getWriteCount)
                .sum());
        log.info("Skip Count: {}", jobExecution.getStepExecutions().stream()
                .mapToLong(org.springframework.batch.core.StepExecution::getSkipCount)
                .sum());

        if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
            log.info("Job COMPLETED SUCCESSFULLY");
        } else if (jobExecution.getStatus() == BatchStatus.FAILED) {
            log.error("Job FAILED with errors");
            jobExecution.getAllFailureExceptions().forEach(ex ->
                log.error("Failure: {}", ex.getMessage(), ex));
        }
        log.info("========================================");
    }
}