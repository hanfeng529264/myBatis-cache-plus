package com.hf.mbcp.api;

import java.util.concurrent.TimeUnit;

/** 分布式/本地缓存锁抽象，支持 tryLock with timeout。 */
public interface CacheLock {

    /**
     * 尝试获取锁。
     * @param waitTime 最大等待时间
     * @param unit     时间单位
     * @return true 表示成功获取锁
     */
    boolean tryLock(long waitTime, TimeUnit unit);

    /** 释放锁 */
    void unlock();

    /** 当前线程是否持有锁 */
    boolean isHeldByCurrentThread();
}
