package com.hf.mbcp.core.key;

import com.google.common.hash.Hashing;
import com.hf.mbcp.api.CacheKeyGenerator;
import com.hf.mbcp.api.PageInfo;
import com.hf.mbcp.api.ShardInfo;
import com.hf.mbcp.core.support.CacheContext;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 默认缓存 key 生成器。
 * <pre>
 * 格式（普通）：{mapper}:{method}:{paramHash}[:{pageHash}]
 * 格式（分片）：{mapper}:{method}:{paramHash}@db{x}t{y}[:{pageHash}]
 *
 * 例：com.example.UserMapper:selectById:a3f2b1c9
 *    com.example.OrderMapper:selectById:d8e4f2a1@db0t2
 * </pre>
 * 使用 Guava MurmurHash3_128 对参数哈希，比 MD5 快 3-5 倍。
 */
public class DefaultCacheKeyGenerator implements CacheKeyGenerator {

    @Override
    public String generate(Class<?> mapper, Method method, Map<String, Object> params,
                           String boundSql, PageInfo pageInfo) {
        String namespace = mapper.getName();
        String methodName = method.getName();
        String paramHash = hashParams(params);

        StringBuilder key = new StringBuilder()
                .append(namespace).append(':')
                .append(methodName).append(':')
                .append(paramHash);

        // 追加分片后缀（从 ThreadLocal 读取，由 ShardingAwareCacheKeyGenerator 或拦截器设置）
        ShardInfo shardInfo = CacheContext.getShardInfo();
        if (shardInfo != null) {
            key.append(shardInfo.toKeySuffix());
        }

        // 追加分页后缀
        if (pageInfo != null) {
            key.append(pageInfo.toKeySuffix());
        }

        return key.toString();
    }

    protected String hashParams(Map<String, Object> params) {
        if (params == null || params.isEmpty()) return "0";
        // 按 key 排序确保幂等，null 值用 "__null__" 表示
        String serialized = params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + Objects.toString(e.getValue(), "__null__"))
                .collect(Collectors.joining("&"));
        return Hashing.murmur3_128()
                .hashString(serialized, StandardCharsets.UTF_8)
                .toString();
    }
}
