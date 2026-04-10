package com.hf.mbcp.api;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 缓存 key 生成器 SPI。
 * 默认实现：{@link com.hf.mbcp.core.key.DefaultCacheKeyGenerator}
 */
public interface CacheKeyGenerator {

    /**
     * 生成缓存 key。
     * @param mapper   Mapper 接口 Class
     * @param method   Mapper 方法
     * @param params   方法参数（key=参数名，value=参数值）
     * @param boundSql 实际执行的 SQL（含占位符），可为 null
     * @param pageInfo 分页参数，非分页查询传 null
     * @return 唯一缓存 key
     */
    String generate(Class<?> mapper, Method method, Map<String, Object> params,
                    String boundSql, PageInfo pageInfo);
}
