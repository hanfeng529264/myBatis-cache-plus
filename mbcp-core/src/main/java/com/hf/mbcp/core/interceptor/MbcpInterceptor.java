package com.hf.mbcp.core.interceptor;

import com.hf.mbcp.annotation.MbcpMapper;
import com.hf.mbcp.annotation.TableHint;
import com.hf.mbcp.annotation.enums.ConsistencyLevel;
import com.hf.mbcp.api.CacheKeyGenerator;
import com.hf.mbcp.api.PageInfo;
import com.hf.mbcp.core.consistency.ConsistencyCoordinator;
import com.hf.mbcp.core.support.CacheContext;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;

/**
 * MyBatis 拦截器：透明缓存模式（非注解路径）。
 * <p>
 * 拦截 {@code Executor.query} 和 {@code Executor.update}：
 * <ul>
 *   <li>query：先查缓存，命中则返回；miss 则透传 DB，结果写入缓存</li>
 *   <li>update：SQL 执行后触发缓存失效</li>
 * </ul>
 * 若 AOP {@code CacheAspect} 已通过注解处理了该调用（{@code CacheContext.isAnnotationHandled()==true}），
 * 拦截器直接放行，避免重复处理。
 * <p>
 * 透明模式仅对 Mapper 接口标注了 {@code @MbcpMapper(autoCache=true)} 的方法生效。
 */
@Intercepts({
    @Signature(type = Executor.class, method = "query",
            args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
    @Signature(type = Executor.class, method = "query",
            args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                    CacheKey.class, BoundSql.class}),
    @Signature(type = Executor.class, method = "update",
            args = {MappedStatement.class, Object.class})
})
public class MbcpInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(MbcpInterceptor.class);

    private final ConsistencyCoordinator coordinator;
    private final CacheKeyGenerator keyGenerator;
    private final long defaultTtlSeconds;

    public MbcpInterceptor(ConsistencyCoordinator coordinator,
                           CacheKeyGenerator keyGenerator,
                           long defaultTtlSeconds) {
        this.coordinator = coordinator;
        this.keyGenerator = keyGenerator;
        this.defaultTtlSeconds = defaultTtlSeconds;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // AOP 注解已处理 → 直接放行
        if (CacheContext.isAnnotationHandled()) {
            return invocation.proceed();
        }

        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        Object parameter = args[1];

        String statementId = ms.getId();
        Class<?> mapperClass = resolveMapperClass(statementId);
        if (mapperClass == null) {
            return invocation.proceed();
        }

        MbcpMapper mbcpMapper = mapperClass.getAnnotation(MbcpMapper.class);

        // 判断是 query 还是 update
        if (invocation.getMethod().getName().equals("update")) {
            return handleUpdate(invocation, ms, mapperClass, mbcpMapper);
        } else {
            return handleQuery(invocation, ms, parameter, args, mapperClass, mbcpMapper);
        }
    }

    @SuppressWarnings("unchecked")
    private Object handleQuery(Invocation invocation, MappedStatement ms, Object parameter,
                               Object[] args, Class<?> mapperClass, MbcpMapper mbcpMapper) throws Throwable {
        // 无 @MbcpMapper 或 autoCache=false → 直接放行
        if (mbcpMapper == null || !mbcpMapper.autoCache()) {
            return invocation.proceed();
        }

        // 检查该方法是否在 excludeMethods 列表中
        String methodName = extractMethodName(ms.getId());
        if (isExcluded(mbcpMapper.excludeMethods(), methodName)) {
            return invocation.proceed();
        }

        // 构造参数 Map
        Map<String, Object> paramMap = buildParamMap(parameter);

        // 分页信息
        RowBounds rowBounds = (RowBounds) args[2];
        PageInfo pageInfo = (rowBounds != null && rowBounds != RowBounds.DEFAULT)
                ? new PageInfo(rowBounds.getOffset(), rowBounds.getLimit())
                : null;

        // 生成缓存 key
        BoundSql boundSql = ms.getBoundSql(parameter);
        String cacheKey;
        try {
            cacheKey = keyGenerator.generate(
                    mapperClass,
                    resolveMethod(mapperClass, methodName),
                    paramMap,
                    boundSql.getSql(),
                    pageInfo
            );
        } catch (Exception e) {
            log.warn("[MBCP] key generation failed for {}, bypass cache: {}", ms.getId(), e.getMessage());
            return invocation.proceed();
        }

        long ttl = mbcpMapper.defaultExpire() > 0 ? mbcpMapper.defaultExpire() : defaultTtlSeconds;
        ConsistencyLevel level = mbcpMapper.consistencyLevel();

        return coordinator.executeQuery(cacheKey, () -> {
            try {
                return invocation.proceed();
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }, ttl, level);
    }

    private Object handleUpdate(Invocation invocation, MappedStatement ms,
                                Class<?> mapperClass, MbcpMapper mbcpMapper) throws Throwable {
        // update 类型判断（INSERT/UPDATE/DELETE）
        SqlCommandType cmdType = ms.getSqlCommandType();
        if (cmdType == SqlCommandType.SELECT || cmdType == SqlCommandType.UNKNOWN) {
            return invocation.proceed();
        }

        Object result = invocation.proceed();

        // 无 @MbcpMapper → 不处理缓存
        if (mbcpMapper == null) {
            return result;
        }

        // 收集表名
        List<String> tableNames = resolveTableNames(mapperClass, ms.getId());

        try {
            // 透明模式：只清除表级缓存（无精确 key）
            coordinator.onWrite(Collections.emptyList(), tableNames, true,
                    mbcpMapper.consistencyLevel());
        } catch (Exception e) {
            log.warn("[MBCP] cache evict failed after update {}: {}", ms.getId(), e.getMessage());
        }

        return result;
    }

    // ────── 工具方法 ──────

    private Class<?> resolveMapperClass(String statementId) {
        int dot = statementId.lastIndexOf('.');
        if (dot < 0) return null;
        String className = statementId.substring(0, dot);
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private String extractMethodName(String statementId) {
        int dot = statementId.lastIndexOf('.');
        return dot >= 0 ? statementId.substring(dot + 1) : statementId;
    }

    private Method resolveMethod(Class<?> mapperClass, String methodName) {
        for (Method m : mapperClass.getMethods()) {
            if (m.getName().equals(methodName)) return m;
        }
        return null;
    }

    private boolean isExcluded(String[] excludeMethods, String methodName) {
        for (String ex : excludeMethods) {
            if (ex.equals(methodName)) return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildParamMap(Object parameter) {
        if (parameter == null) return Collections.emptyMap();
        if (parameter instanceof Map) return (Map<String, Object>) parameter;
        return Map.of("param", parameter);
    }

    private List<String> resolveTableNames(Class<?> mapperClass, String statementId) {
        List<String> names = new ArrayList<>();

        // 1. 类级 @TableHint
        TableHint classHint = mapperClass.getAnnotation(TableHint.class);
        if (classHint != null) {
            names.addAll(Arrays.asList(classHint.tables()));
        }

        // 2. 方法级 @TableHint
        String methodName = extractMethodName(statementId);
        for (Method m : mapperClass.getMethods()) {
            if (m.getName().equals(methodName)) {
                TableHint methodHint = m.getAnnotation(TableHint.class);
                if (methodHint != null) {
                    names.addAll(Arrays.asList(methodHint.tables()));
                }
                break;
            }
        }

        return names;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 通过构造函数注入，此处无需处理
    }
}
