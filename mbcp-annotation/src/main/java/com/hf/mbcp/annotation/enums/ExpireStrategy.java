package com.hf.mbcp.annotation.enums;

/** L1 本地缓存过期策略 */
public enum ExpireStrategy {
    /** 写入后固定 TTL 过期（expireAfterWrite） */
    WRITE,
    /** 最近访问后计时过期（expireAfterAccess），适合热点数据保活 */
    ACCESS
}
