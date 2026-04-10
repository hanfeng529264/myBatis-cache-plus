package com.hf.mbcp.cache.redis;

import com.hf.mbcp.api.CacheLock;
import org.redisson.api.RReadWriteLock;

import java.util.concurrent.TimeUnit;

/**
 * Redisson RReadWriteLock 包装（Level D 强一致专用）。
 * 读锁：共享，多个读请求并发持有。
 * 写锁：排他，写时阻塞所有读写。
 */
public class RedissonReadWriteLock {

    private final RReadWriteLock rwLock;

    public RedissonReadWriteLock(RReadWriteLock rwLock) {
        this.rwLock = rwLock;
    }

    /** 获取读锁（CacheLock 包装） */
    public CacheLock readLock() {
        return new RLockWrapper(rwLock.readLock());
    }

    /** 获取写锁（CacheLock 包装） */
    public CacheLock writeLock() {
        return new RLockWrapper(rwLock.writeLock());
    }

    private record RLockWrapper(org.redisson.api.RLock lock) implements CacheLock {
        @Override
        public boolean tryLock(long waitTime, TimeUnit unit) {
            try {
                return lock.tryLock(waitTime, unit);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        @Override
        public void unlock() {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
        @Override
        public boolean isHeldByCurrentThread() {
            return lock.isHeldByCurrentThread();
        }
    }
}
