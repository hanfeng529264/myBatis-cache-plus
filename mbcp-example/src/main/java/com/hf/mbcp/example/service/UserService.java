package com.hf.mbcp.example.service;

import com.hf.mbcp.annotation.enums.ConsistencyLevel;
import com.hf.mbcp.example.entity.User;
import com.hf.mbcp.example.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 演示 Level A/B/C/D 四种一致性级别及事务感知。
 */
@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** Level A — 极少变更数据（字典、菜单），忽略一致性，完全依赖 TTL */
    public User getByIdIgnoreConsistency(Long id) {
        return userMapper.selectByIdLevelA(id);
    }

    /** Level B — 尽力一致，写后异步删 L2，脏读窗口 = L1 TTL */
    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    /** Level C — 最终一致（默认），事务感知：提交后才失效 + 延迟双删 */
    public List<User> getByAge(Integer age) {
        return userMapper.selectByAge(age);
    }

    /** Level D — 强一致，Redis 读写锁 + write-through（需配置 Redis） */
    public User getByEmail(String email) {
        return userMapper.selectByEmail(email);
    }

    /** 事务感知示例：Level C 在 @Transactional 方法中，缓存失效推迟到事务提交后 */
    @Transactional
    public int updateUser(User user) {
        int rows = userMapper.updateById(user);
        // 缓存失效将在事务 commit 之后执行（Level C 事务感知）
        return rows;
    }

    @Transactional
    public int createUser(User user) {
        return userMapper.insert(user);
    }

    public int deleteUser(Long id) {
        return userMapper.deleteById(id);
    }

    public List<User> getAllUsers() {
        return userMapper.selectAll();
    }

    public long countUsers() {
        return userMapper.count();
    }
}
