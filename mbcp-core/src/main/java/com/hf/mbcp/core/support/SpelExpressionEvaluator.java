package com.hf.mbcp.core.support;

import com.hf.mbcp.api.CacheConditionEvaluator;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SpEL 表达式求值器，处理 @Cacheable.condition/unless/key/enabled。
 * <p>支持变量：
 * <ul>
 *   <li>{@code #paramName} — 方法参数</li>
 *   <li>{@code #result}    — 方法返回值（仅 unless/key 中可用）</li>
 *   <li>{@code #root.args[0]} — 参数数组</li>
 * </ul>
 */
public class SpelExpressionEvaluator implements CacheConditionEvaluator {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    /** 表达式缓存，避免重复解析 */
    private final Map<String, Expression> exprCache = new ConcurrentHashMap<>(64);

    @Override
    public boolean evaluateCondition(String conditionExpr, Method method, Map<String, Object> params) {
        if (conditionExpr == null || conditionExpr.isBlank()) return true;
        Object result = evaluate(conditionExpr, method, params, null);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public boolean evaluateUnless(String unlessExpr, Method method, Map<String, Object> params, Object result) {
        if (unlessExpr == null || unlessExpr.isBlank()) return false;
        Object val = evaluate(unlessExpr, method, params, result);
        return Boolean.TRUE.equals(val);
    }

    @Override
    public String evaluateKey(String keyExpr, Method method, Map<String, Object> params, Object result) {
        if (keyExpr == null || keyExpr.isBlank()) return null;
        Object val = evaluate(keyExpr, method, params, result);
        return val != null ? val.toString() : null;
    }

    @Override
    public boolean evaluateEnabled(String enabledExpr, Method method, Map<String, Object> params) {
        if (enabledExpr == null || enabledExpr.isBlank() || "true".equalsIgnoreCase(enabledExpr)) return true;
        if ("false".equalsIgnoreCase(enabledExpr)) return false;
        Object val = evaluate(enabledExpr, method, params, null);
        return !Boolean.FALSE.equals(val);
    }

    private Object evaluate(String expr, Method method, Map<String, Object> params, Object returnValue) {
        Expression expression = exprCache.computeIfAbsent(expr, PARSER::parseExpression);
        EvaluationContext ctx = buildContext(method, params, returnValue);
        return expression.getValue(ctx);
    }

    private EvaluationContext buildContext(Method method, Map<String, Object> params, Object returnValue) {
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        // 注入参数名 → 值
        params.forEach(ctx::setVariable);
        // #result 变量
        if (returnValue != null) ctx.setVariable("result", returnValue);
        return ctx;
    }
}
