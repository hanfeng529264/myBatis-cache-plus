package com.hf.mbcp.core.key;

import com.hf.mbcp.api.CacheKeyGenerator;
import com.hf.mbcp.api.PageInfo;
import com.hf.mbcp.api.ShardInfo;
import com.hf.mbcp.api.ShardInfoExtractor;
import com.hf.mbcp.core.support.CacheContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 分库分表感知的缓存 key 生成器。
 * 在 {@link DefaultCacheKeyGenerator} 基础上，通过 {@link ShardInfoExtractor}
 * 提取 ShardingSphere 路由结果并追加分片后缀。
 */
public class ShardingAwareCacheKeyGenerator extends DefaultCacheKeyGenerator {

    private static final Logger log = LoggerFactory.getLogger(ShardingAwareCacheKeyGenerator.class);
    private final ShardInfoExtractor shardInfoExtractor;

    public ShardingAwareCacheKeyGenerator(ShardInfoExtractor shardInfoExtractor) {
        this.shardInfoExtractor = shardInfoExtractor;
    }

    @Override
    public String generate(Class<?> mapper, Method method, Map<String, Object> params,
                           String boundSql, PageInfo pageInfo) {
        // 提取分片信息并设置到 ThreadLocal，供父类读取
        try {
            ShardInfo shardInfo = shardInfoExtractor.extract(mapper, method, params);
            CacheContext.setShardInfo(shardInfo);
        } catch (Exception e) {
            log.warn("[MBCP-Sharding] failed to extract shard info for {}.{}: {}",
                    mapper.getSimpleName(), method.getName(), e.getMessage());
        }
        return super.generate(mapper, method, params, boundSql, pageInfo);
    }
}
