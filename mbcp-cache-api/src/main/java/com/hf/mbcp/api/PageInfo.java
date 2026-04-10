package com.hf.mbcp.api;

/** 分页参数（用于生成分页查询的缓存 key 后缀）。 */
public record PageInfo(long offset, int size) {

    public static PageInfo of(long offset, int size) {
        return new PageInfo(offset, size);
    }

    /** 生成 key 后缀，格式：":p{offset}s{size}" */
    public String toKeySuffix() {
        return ":p" + offset + "s" + size;
    }
}
