package com.hf.mbcp.annotation.enums;

/** 缓存类型（multi-level 组合模式） */
public enum CacheType {
    /** 仅本地缓存 L1（Caffeine） */
    LOCAL,
    /** 仅分布式缓存 L2（Redis） */
    REDIS,
    /** L1 + L2 混合（默认推荐） */
    HYBRID,
    /** 禁用缓存（透传到 DB） */
    NONE
}
