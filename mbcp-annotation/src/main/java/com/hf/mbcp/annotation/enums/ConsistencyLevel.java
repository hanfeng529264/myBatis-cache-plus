package com.hf.mbcp.annotation.enums;

/**
 * 缓存一致性级别
 * <pre>
 *  A - IGNORE       : 忽略一致，纯 TTL 过期，写不清缓存；适合字典/公告等极少变更数据
 *  B - BEST_EFFORT  : 尽力一致，写后清自身 L1 + 异步删 L2，其他节点 L1 靠 TTL 过期
 *  C - EVENTUAL     : 最终一致（默认），广播清所有节点 L1 + 事务感知 + 延迟双删
 *  D - STRONG       : 强一致，Redisson ReadWriteLock + write-through，必须配置 Redis
 * </pre>
 */
public enum ConsistencyLevel {
    /** 忽略一致性，完全依赖 TTL 过期 */
    IGNORE,
    /** 尽力一致，不做广播 */
    BEST_EFFORT,
    /** 最终一致（默认） */
    EVENTUAL,
    /** 强一致，需要 Redis */
    STRONG
}
