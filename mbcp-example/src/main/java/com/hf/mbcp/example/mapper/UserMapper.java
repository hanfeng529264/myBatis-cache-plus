package com.hf.mbcp.example.mapper;

import com.hf.mbcp.annotation.*;
import com.hf.mbcp.annotation.enums.ConsistencyLevel;
import com.hf.mbcp.annotation.enums.EvictScope;
import com.hf.mbcp.example.entity.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 演示 MBCP 四种注解 + A/B/C/D 四级一致性的 Mapper。
 */
@Mapper
@MbcpMapper(defaultExpire = 300, autoCache = true)
@TableHint(tables = {"users"})
public interface UserMapper {

    // ── Level A (IGNORE)：字典类数据，完全依赖 TTL ──
    @Cacheable(expire = 3600, consistencyLevel = ConsistencyLevel.IGNORE)
    @Select("SELECT * FROM users WHERE id = #{id}")
    User selectByIdLevelA(@Param("id") Long id);

    // ── Level B (BEST_EFFORT)：普通查询，尽力一致 ──
    @Cacheable(key = "'user:' + #id", expire = 600, consistencyLevel = ConsistencyLevel.BEST_EFFORT)
    @Select("SELECT * FROM users WHERE id = #{id}")
    User selectById(@Param("id") Long id);

    // ── Level C (EVENTUAL，默认)：最终一致 ──
    @Cacheable(key = "'user:list:age:' + #age", expire = 300)
    @Select("SELECT * FROM users WHERE age = #{age}")
    List<User> selectByAge(@Param("age") Integer age);

    // ── Level D (STRONG)：强一致，需要 Redis ──
    @Cacheable(key = "'user:email:' + #email", expire = 600, consistencyLevel = ConsistencyLevel.STRONG)
    @Select("SELECT * FROM users WHERE email = #{email}")
    User selectByEmail(@Param("email") String email);

    // ── CacheEvict：更新后失效 ──
    @CacheEvict(key = "'user:' + #user.id", doubleEvict = true)
    @Update("UPDATE users SET name=#{user.name}, email=#{user.email}, age=#{user.age} WHERE id=#{user.id}")
    int updateById(@Param("user") User user);

    // ── CacheEvict + 表级清除 ──
    @CacheEvict(scope = EvictScope.TABLE)
    @TableHint(tables = {"users"})
    @Insert("INSERT INTO users(name, email, age, create_time) VALUES(#{name}, #{email}, #{age}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    // ── CacheEvict：删除 ──
    @CacheEvict(key = "'user:' + #id", beforeInvocation = true)
    @Delete("DELETE FROM users WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    // ── Caching：组合操作 ──
    @Caching(
            evict = {@CacheEvict(key = "'user:' + #user.id")},
            put   = {@CachePut(key = "'user:' + #user.id", expire = 300)}
    )
    @Update("UPDATE users SET name=#{user.name}, email=#{user.email} WHERE id=#{user.id}")
    int updateAndRefresh(@Param("user") User user);

    @Select("SELECT * FROM users")
    List<User> selectAll();

    @Select("SELECT COUNT(*) FROM users")
    long count();
}
