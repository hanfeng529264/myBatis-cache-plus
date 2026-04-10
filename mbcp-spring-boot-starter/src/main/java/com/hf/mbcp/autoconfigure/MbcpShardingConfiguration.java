package com.hf.mbcp.autoconfigure;

import com.hf.mbcp.api.ShardInfo;
import com.hf.mbcp.api.ShardInfoExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * ShardingSphere 分库分表适配配置。
 * <p>仅当 classpath 中存在 ShardingSphere 时生效。
 */
@Configuration
@ConditionalOnClass(name = "org.apache.shardingsphere.sharding.api.ShardingRuleConfiguration")
public class MbcpShardingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MbcpShardingConfiguration.class);

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(ShardInfoExtractor.class)
    public ShardInfoExtractor mbcpShardInfoExtractor() {
        // ShardingSphere 5.x 通过 RouteContextHolder 获取路由上下文
        // 实际项目可替换为读取 RouteContextHolder.getRouteContext() 的实现
        return new ShardingSphereShardInfoExtractor();
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(
            com.hf.mbcp.api.CacheKeyGenerator.class)
    public com.hf.mbcp.core.key.ShardingAwareCacheKeyGenerator mbcpShardingKeyGenerator(
            ShardInfoExtractor extractor) {
        return new com.hf.mbcp.core.key.ShardingAwareCacheKeyGenerator(extractor);
    }

    /**
     * ShardingSphere 路由信息提取器。
     * 通过反射读取 ShardingSphere RouteContextHolder，无强依赖。
     */
    static class ShardingSphereShardInfoExtractor implements ShardInfoExtractor {

        @Override
        public ShardInfo extract(Class<?> mapper, Method method, Map<String, Object> params) {
            try {
                // 通过反射获取 ShardingSphere 路由上下文，避免强依赖
                Class<?> holderClass = Class.forName(
                        "org.apache.shardingsphere.infra.route.context.RouteContextHolder",
                        false, Thread.currentThread().getContextClassLoader());
                Object routeContext = holderClass.getMethod("get").invoke(null);
                if (routeContext == null) return null;

                // RouteContext.getRouteUnits() 获取路由单元
                Object routeUnits = routeContext.getClass().getMethod("getRouteUnits")
                        .invoke(routeContext);
                if (routeUnits instanceof Iterable<?> iterable) {
                    for (Object unit : iterable) {
                        // 取第一个路由单元的物理表信息
                        Object ds = unit.getClass().getMethod("getDataSourceMapper")
                                .invoke(unit);
                        Object tables = unit.getClass().getMethod("getTableMappers")
                                .invoke(unit);
                        if (tables instanceof Iterable<?> tableIterable) {
                            for (Object tableMapper : tableIterable) {
                                String physicalTable = tableMapper.getClass()
                                        .getMethod("getActualName").invoke(tableMapper).toString();
                                return new ShardInfo(0, 0, physicalTable);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.trace("[MBCP-Sharding] RouteContext not available: {}", e.getMessage());
            }
            return null;
        }
    }
}
