package com.hf.mbcp.annotation;

import java.lang.annotation.*;

/**
 * 显式声明方法涉及的逻辑表名，用于精确推断缓存失效范围。
 * <p>在动态 SQL、多表 JOIN、分库分表等场景下，框架无法自动推断表名时，
 * 请使用此注解显式声明。
 * <p>示例：
 * <pre>
 * {@literal @}CacheEvict
 * {@literal @}TableHint(tables = {"order", "order_item"}, logicalTable = true)
 * int updateOrderWithItems(Order order);
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TableHint {

    /** 涉及的表名列表 */
    String[] tables();

    /**
     * tables 中的名称是否为逻辑表名（分库分表场景）。
     * true  = 逻辑表名，失效时会覆盖所有物理分片
     * false = 物理表名（默认）
     */
    boolean logicalTable() default false;
}
