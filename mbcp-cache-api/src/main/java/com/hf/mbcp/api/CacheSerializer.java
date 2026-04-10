package com.hf.mbcp.api;

import com.fasterxml.jackson.core.type.TypeReference;

/** 缓存序列化器，用于 Redis 存储。 */
public interface CacheSerializer {

    /** 序列化对象为字节数组 */
    byte[] serialize(Object value);

    /**
     * 反序列化，保留泛型类型信息。
     * @param bytes   字节数组
     * @param typeRef Jackson TypeReference，携带泛型信息（如 {@code new TypeReference<List<User>>(){}}）
     */
    <T> T deserialize(byte[] bytes, TypeReference<T> typeRef);

    /** 序列化器名称（用于配置区分） */
    String name();
}
