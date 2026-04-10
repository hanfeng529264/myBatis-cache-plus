package com.hf.mbcp.autoconfigure;

import com.hf.mbcp.cache.local.LocalCacheProperties;

/**
 * Java Config 接口，允许用户以编程方式覆盖 yml 中的本地缓存配置。
 * <p>
 * 在 Spring 容器中注册一个实现了此接口的 Bean，其配置优先级高于 {@code application.yml}。
 *
 * <pre>{@code
 * @Bean
 * public MbcpLocalCacheConfig customL1Config() {
 *     return builder -> builder
 *         .maxMemoryMb(512)
 *         .expireAfterWrite(600)
 *         .initialCapacity(5000);
 * }
 * }</pre>
 */
@FunctionalInterface
public interface MbcpLocalCacheConfig {

    /**
     * 对 {@link LocalCacheProperties.Builder} 进行自定义配置。
     */
    void configure(LocalCacheProperties.Builder builder);
}
