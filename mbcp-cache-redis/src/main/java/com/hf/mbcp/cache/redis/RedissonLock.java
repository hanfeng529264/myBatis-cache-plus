package com.hf.mbcp.cache.redis;

import com.hf.mbcp.api.CacheLock;
import org.redisson.api.RLock;

import java.util.concurrent.TimeUnit;

/** Redisson RLock 的 CacheLock 包装（Level C 击穿锁）。 */
public class RedissonLock implements CacheLock {

    private final RLock rLock;

    public RedissonLock(RLock rLock) {
        this.rLock = rLock;
    }

    @Override
    public boolean tryLock(long waitTime, TimeUnit unit) {
        try {
            return rLock.tryLock(waitTime, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void unlock() {
        if (rLock.isHeldByCurrentThread()) {
            rLock.unlock();
        }
    }

    @Override
    public boolean isHeldByCurrentThread() {
        return rLock.isHeldByCurrentThread();
    }
}
