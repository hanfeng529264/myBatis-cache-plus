package com.hf.mbcp.api;

/**
 * 分库分表路由信息，由 ShardInfoExtractor 提取，追加到缓存 key 末尾。
 * key 格式：{namespace}:{method}:{paramHash}@db{dbIndex}t{tableIndex}
 */
public record ShardInfo(
        /** 数据库编号（0-based） */
        int dbIndex,
        /** 物理表编号（0-based） */
        int tableIndex,
        /** 物理表全名（如 order_2） */
        String physicalTableName
) {
    /** 生成追加到 key 末尾的分片后缀 */
    public String toKeySuffix() {
        return "@db" + dbIndex + "t" + tableIndex;
    }
}
