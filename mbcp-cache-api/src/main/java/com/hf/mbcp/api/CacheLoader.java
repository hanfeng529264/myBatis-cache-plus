package com.hf.mbcp.api;

import java.util.Collection;
import java.util.Map;

/** 批量缓存预热加载器，在应用启动时预加载热点数据到缓存。 */
public interface CacheLoader<K, V> {

    /**
     * 批量加载数据。
     * @param keys 需要加载的 key 集合
     * @return key → value 映射
     */
    Map<K, V> loadAll(Collection<K> keys);

    /** 对应的 Mapper namespace（用于确定写入哪个缓存 namespace） */
    String getNamespace();
}
