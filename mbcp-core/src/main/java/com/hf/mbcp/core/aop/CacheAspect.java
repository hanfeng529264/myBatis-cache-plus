package com.hf.mbcp.core.aop;

import com.hf.mbcp.annotation.*;
import com.hf.mbcp.annotation.enums.ConsistencyLevel;
import com.hf.mbcp.annotation.enums.EvictScope;
import com.hf.mbcp.api.CacheKeyGenerator;
import com.hf.mbcp.core.consistency.ConsistencyCoordinator;
import com.hf.mbcp.core.support.CacheContext;
import com.hf.mbcp.core.support.SpelExpressionEvaluator;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;

/**
 * 注解驱动的缓存切面。
 * <p>
 * 处理 {@code @Cacheable / @CacheEvict / @CachePut / @Caching} 四种注解。
 * 每次调用前设置 {@code CacheContext.setAnnotationHandled(true)}，确保 {@code MbcpInterceptor} 不重复处理。
 */
@Aspect
public class CacheAspect {

    private static final Logger log = LoggerFactory.getLogger(CacheAspect.class);

    private final ConsistencyCoordinator coordinator;
    private final CacheKeyGenerator keyGenerator;
    private final SpelExpressionEvaluator spelEvaluator;
    private final long defaultTtlSeconds;

    public CacheAspect(ConsistencyCoordinator coordinator,
                       CacheKeyGenerator keyGenerator,
                       SpelExpressionEvaluator spelEvaluator,
                       long defaultTtlSeconds) {
        this.coordinator = coordinator;
        this.keyGenerator = keyGenerator;
        this.spelEvaluator = spelEvaluator;
        this.defaultTtlSeconds = defaultTtlSeconds;
    }

    // ────── @Cacheable ──────

    @Around("@annotation(cacheable)")
    public Object aroundCacheable(ProceedingJoinPoint pjp, Cacheable cacheable) throws Throwable {
        MethodInfo mi = MethodInfo.of(pjp);
        Map<String, Object> params = buildParams(mi.method(), pjp.getArgs());

        // enabled 是 SpEL String，求值
        if (!spelEvaluator.evaluateEnabled(cacheable.enabled(), mi.method(), params)) {
            return pjp.proceed();
        }

        // condition 为 false → 跳过缓存
        if (!spelEvaluator.evaluateCondition(cacheable.condition(), mi.method(), params)) {
            return pjp.proceed();
        }

        String cacheKey = resolveKey(cacheable.key(), cacheable.cacheName(), mi, params);
        long ttl = cacheable.expire() > 0 ? cacheable.expire() : defaultTtlSeconds;

        CacheContext.setAnnotationHandled(true);
        try {
            return coordinator.executeQuery(cacheKey, () -> {
                try {
                    Object result = pjp.proceed();
                    // unless 为 true → 不缓存结果（返回 null 让上层不写缓存）
                    if (spelEvaluator.evaluateUnless(cacheable.unless(), mi.method(), params, result)) {
                        return null;
                    }
                    return result;
                } catch (Throwable t) {
                    throw new RuntimeException("[MBCP] @Cacheable proceed failed", t);
                }
            }, ttl, cacheable.consistencyLevel());
        } finally {
            CacheContext.clear();
        }
    }

    // ────── @CacheEvict ──────

    @Around("@annotation(evict)")
    public Object aroundCacheEvict(ProceedingJoinPoint pjp, CacheEvict evict) throws Throwable {
        MethodInfo mi = MethodInfo.of(pjp);
        Map<String, Object> params = buildParams(mi.method(), pjp.getArgs());

        List<String> cacheKeys = resolveEvictKeys(evict, mi, params);
        List<String> tableNames = resolveTableNames(mi.method());

        CacheContext.setAnnotationHandled(true);
        try {
            if (evict.beforeInvocation()) {
                doEvict(cacheKeys, tableNames, evict.doubleEvict(), ConsistencyLevel.EVENTUAL);
            }

            Object result = pjp.proceed();

            if (evict.afterInvocation()) {
                doEvict(cacheKeys, tableNames, evict.doubleEvict(), ConsistencyLevel.EVENTUAL);
            }

            return result;
        } finally {
            CacheContext.clear();
        }
    }

    // ────── @CachePut ──────

    @Around("@annotation(put)")
    public Object aroundCachePut(ProceedingJoinPoint pjp, CachePut put) throws Throwable {
        MethodInfo mi = MethodInfo.of(pjp);
        Map<String, Object> params = buildParams(mi.method(), pjp.getArgs());

        if (!spelEvaluator.evaluateCondition(put.condition(), mi.method(), params)) {
            return pjp.proceed();
        }

        CacheContext.setAnnotationHandled(true);
        try {
            Object result = pjp.proceed();

            if (!spelEvaluator.evaluateUnless(put.unless(), mi.method(), params, result)) {
                String cacheKey = resolveKey(put.key(), put.cacheName(), mi, params);
                long ttl = put.expire() > 0 ? put.expire() : defaultTtlSeconds;
                final Object finalResult = result;
                coordinator.executeQuery(cacheKey, () -> finalResult, ttl, ConsistencyLevel.EVENTUAL);
            }

            return result;
        } finally {
            CacheContext.clear();
        }
    }

    // ────── @Caching ──────

    @Around("@annotation(caching)")
    public Object aroundCaching(ProceedingJoinPoint pjp, Caching caching) throws Throwable {
        MethodInfo mi = MethodInfo.of(pjp);
        Map<String, Object> params = buildParams(mi.method(), pjp.getArgs());

        CacheContext.setAnnotationHandled(true);
        try {
            // beforeInvocation evicts
            for (CacheEvict evict : caching.evict()) {
                if (evict.beforeInvocation()) {
                    List<String> keys = resolveEvictKeys(evict, mi, params);
                    List<String> tables = resolveTableNames(mi.method());
                    doEvict(keys, tables, evict.doubleEvict(), ConsistencyLevel.EVENTUAL);
                }
            }

            // @Cacheable — check first Cacheable
            Object result = null;
            boolean cacheHit = false;
            if (caching.cacheable().length > 0) {
                Cacheable cacheable = caching.cacheable()[0];
                boolean enabled = spelEvaluator.evaluateEnabled(cacheable.enabled(), mi.method(), params);
                boolean condition = spelEvaluator.evaluateCondition(cacheable.condition(), mi.method(), params);
                if (enabled && condition) {
                    String key = resolveKey(cacheable.key(), cacheable.cacheName(), mi, params);
                    long ttl = cacheable.expire() > 0 ? cacheable.expire() : defaultTtlSeconds;
                    result = coordinator.executeQuery(key, () -> {
                        try { return pjp.proceed(); } catch (Throwable t) { throw new RuntimeException(t); }
                    }, ttl, cacheable.consistencyLevel());
                    cacheHit = true;
                }
            }

            if (!cacheHit) {
                result = pjp.proceed();
            }

            // afterInvocation evicts
            final Object finalResult = result;
            for (CacheEvict evict : caching.evict()) {
                if (evict.afterInvocation()) {
                    List<String> keys = resolveEvictKeys(evict, mi, params);
                    List<String> tables = resolveTableNames(mi.method());
                    doEvict(keys, tables, evict.doubleEvict(), ConsistencyLevel.EVENTUAL);
                }
            }

            // CachePut
            for (CachePut put : caching.put()) {
                boolean condition = spelEvaluator.evaluateCondition(put.condition(), mi.method(), params);
                boolean unless = spelEvaluator.evaluateUnless(put.unless(), mi.method(), params, finalResult);
                if (condition && !unless) {
                    String key = resolveKey(put.key(), put.cacheName(), mi, params);
                    long ttl = put.expire() > 0 ? put.expire() : defaultTtlSeconds;
                    coordinator.executeQuery(key, () -> finalResult, ttl, ConsistencyLevel.EVENTUAL);
                }
            }

            return result;
        } finally {
            CacheContext.clear();
        }
    }

    // ────── 工具方法 ──────

    private void doEvict(List<String> cacheKeys, List<String> tableNames,
                         boolean doubleEvict, ConsistencyLevel level) {
        try {
            coordinator.onWrite(cacheKeys, tableNames, doubleEvict, level);
        } catch (Exception e) {
            log.warn("[MBCP] doEvict failed: {}", e.getMessage());
        }
    }

    private String resolveKey(String spelKey, String cacheName,
                               MethodInfo mi, Map<String, Object> params) {
        if (spelKey != null && !spelKey.isBlank()) {
            String evaluated = spelEvaluator.evaluateKey(spelKey, mi.method(), params, null);
            return evaluated != null ? evaluated : buildDefaultKey(cacheName, mi, params);
        }
        return buildDefaultKey(cacheName, mi, params);
    }

    private String buildDefaultKey(String cacheName, MethodInfo mi, Map<String, Object> params) {
        try {
            String generated = keyGenerator.generate(mi.declaringClass(), mi.method(), params, "", null);
            return cacheName != null && !cacheName.isBlank() ? cacheName + ":" + generated : generated;
        } catch (Exception e) {
            log.warn("[MBCP] key generation failed: {}", e.getMessage());
            return mi.declaringClass().getSimpleName() + ":" + mi.method().getName() + ":" + params.hashCode();
        }
    }

    private List<String> resolveEvictKeys(CacheEvict evict, MethodInfo mi, Map<String, Object> params) {
        List<String> keys = new ArrayList<>();
        EvictScope scope = evict.scope();
        if (scope == EvictScope.TABLE || scope == EvictScope.NAMESPACE) {
            return keys;
        }
        String spelKey = evict.key();
        if (spelKey != null && !spelKey.isBlank()) {
            String evaluated = spelEvaluator.evaluateKey(spelKey, mi.method(), params, null);
            if (evaluated != null) {
                keys.add(evaluated);
            }
        }
        return keys;
    }

    private List<String> resolveTableNames(Method method) {
        List<String> names = new ArrayList<>();
        TableHint hint = method.getAnnotation(TableHint.class);
        if (hint != null) Collections.addAll(names, hint.tables());
        TableHint classHint = method.getDeclaringClass().getAnnotation(TableHint.class);
        if (classHint != null) Collections.addAll(names, classHint.tables());
        return names;
    }

    private Map<String, Object> buildParams(Method method, Object[] args) {
        Map<String, Object> params = new LinkedHashMap<>();
        java.lang.reflect.Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            params.put(parameters[i].getName(), args[i]);
        }
        return params;
    }

    // ────── 内部记录类 ──────

    private record MethodInfo(Class<?> declaringClass, Method method) {
        static MethodInfo of(ProceedingJoinPoint pjp) {
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            return new MethodInfo(pjp.getTarget().getClass(), sig.getMethod());
        }
    }
}
