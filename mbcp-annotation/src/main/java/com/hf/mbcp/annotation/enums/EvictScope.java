package com.hf.mbcp.annotation.enums;

/** 缓存失效范围 */
public enum EvictScope {
    /** 自动推断（优先使用 key 表达式，无则按表名/方法维度） */
    AUTO,
    /** 精确 key（需配合 key 属性的 SpEL 表达式） */
    KEY,
    /** 方法维度：清除 namespace:method 前缀下所有 key */
    METHOD,
    /** 表维度：清除涉及表名的所有 key（需 @TableHint 或自动推断） */
    TABLE,
    /** Mapper 维度：清除该 Mapper 所有 key */
    NAMESPACE
}
