package com.hf.mbcp.api;

/**
 * 防穿透空值占位符（单例）。
 * 当 DB 查询结果为 null 时，存入此占位符防止缓存穿透。
 */
public final class NullValue {

    public static final NullValue INSTANCE = new NullValue();

    private NullValue() {}

    @Override
    public String toString() {
        return "__NULL__";
    }
}
