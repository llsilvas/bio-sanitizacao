package bio.prodesp.deduplicacao.batch.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {

    private final RedissonClient redissonClient;

    private static final String LOCK_PREFIX = "batch:lock:";
    private static final long DEFAULT_WAIT_TIME = 10;
    private static final long DEFAULT_LEASE_TIME = 300;

    /**
     * Tenta adquirir um lock distribuído
     *
     * @param lockKey chave do lock
     * @return true se conseguiu adquirir o lock
     */
    public boolean tryLock(String lockKey) {
        return tryLock(lockKey, DEFAULT_WAIT_TIME, DEFAULT_LEASE_TIME);
    }

    /**
     * Tenta adquirir um lock distribuído com timeout customizado
     *
     * @param lockKey chave do lock
     * @param waitTime tempo de espera em segundos
     * @param leaseTime tempo de lease em segundos
     * @return true se conseguiu adquirir o lock
     */
    public boolean tryLock(String lockKey, long waitTime, long leaseTime) {
        String fullKey = LOCK_PREFIX + lockKey;
        RLock lock = redissonClient.getLock(fullKey);

        try {
            boolean acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);
            if (acquired) {
                log.info("Lock acquired successfully: {}", fullKey);
            } else {
                log.warn("Failed to acquire lock: {}", fullKey);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while trying to acquire lock: {}", fullKey, e);
            return false;
        }
    }

    /**
     * Libera o lock distribuído
     *
     * @param lockKey chave do lock
     */
    public void unlock(String lockKey) {
        String fullKey = LOCK_PREFIX + lockKey;
        RLock lock = redissonClient.getLock(fullKey);

        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.info("Lock released: {}", fullKey);
        } else {
            log.warn("Lock not held by current thread: {}", fullKey);
        }
    }

    /**
     * Verifica se o lock está ativo
     *
     * @param lockKey chave do lock
     * @return true se o lock está ativo
     */
    public boolean isLocked(String lockKey) {
        String fullKey = LOCK_PREFIX + lockKey;
        RLock lock = redissonClient.getLock(fullKey);
        return lock.isLocked();
    }

    /**
     * Força a liberação do lock (usar com cuidado)
     *
     * @param lockKey chave do lock
     */
    public void forceUnlock(String lockKey) {
        String fullKey = LOCK_PREFIX + lockKey;
        RLock lock = redissonClient.getLock(fullKey);

        if (lock.isLocked()) {
            lock.forceUnlock();
            log.warn("Lock force unlocked: {}", fullKey);
        }
    }
}