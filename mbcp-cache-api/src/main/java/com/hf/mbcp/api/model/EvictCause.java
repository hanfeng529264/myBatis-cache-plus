package com.hf.mbcp.api.model;

/** 缓存条目被淘汰/清除的原因 */
public enum EvictCause {
    /** 超过最大内存/条目数被淘汰 */
    SIZE,
    /** TTL 到期自然过期 */
    EXPIRED,
    /** 显式调用 evict/clear */
    EXPLICIT,
    /** key 被新值覆盖（put 替换） */
    REPLACED
}
