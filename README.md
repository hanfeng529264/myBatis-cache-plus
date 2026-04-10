# MyBatis Cache Plus (MBCP)

> **高性能、透明、生产级的 MyBatis 多级缓存框架**

[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![MyBatis](https://img.shields.io/badge/MyBatis-3.0.3-red.svg)](https://mybatis.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Build](https://img.shields.io/badge/Build-Maven-orange.svg)](https://maven.apache.org/)

---

## 目录

- [简介](#简介)
- [特性](#特性)
- [架构](#架构)
- [模块结构](#模块结构)
- [快速开始](#快速开始)
- [注解使用](#注解使用)
- [一致性级别](#一致性级别)
- [配置参考](#配置参考)
- [Java Config 双通道配置](#java-config-双通道配置)
- [扩展点](#扩展点)
- [监控与运维](#监控与运维)
- [分库分表支持](#分库分表支持)
- [从源码构建](#从源码构建)
- [设计文档](#设计文档)

---

## 简介

MBCP 是一个置于 **MyBatis 与数据库之间**的增强缓存框架，对业务代码**几乎零侵入**：

- 通过 MyBatis **插件拦截器** 实现透明缓存（不改一行 Mapper 代码即可启用）
- 通过 Spring **AOP 注解** 提供精细控制（类似 Spring Cache，但功能更强）
- 支持 **本地缓存（Caffeine L1）** + **分布式缓存（Redis L2）** 任意组合
- 内置 **4 级一致性**（IGNORE / BEST_EFFORT / EVENTUAL / STRONG），覆盖从字典表到强一致金融场景
- 集成 **Micrometer 指标** + **Spring Actuator** 端点，生产可观测

---

## 特性

| 特性 | 说明 |
|------|------|
| 🔌 **透明接入** | `@MbcpMapper(autoCache=true)` 一行注解，无需修改现有 Mapper |
| 🗄️ **多级缓存** | L1（Caffeine）→ L2（Redis）→ DB，支持 LOCAL / REDIS / HYBRID 模式 |
| 🎯 **细粒度 Key** | Mapper + 方法 + 参数哈希（MurmurHash128）+ 分页 + 分片后缀，精确隔离 |
| 🔒 **4 级一致性** | IGNORE(A) / BEST_EFFORT(B) / EVENTUAL(C) / STRONG(D)，按场景选择 |
| 🛡️ **三大防护** | 防穿透（NullValue 占位）/ 防雪崩（随机 TTL 偏移）/ 防击穿（互斥锁双重检查）|
| 📡 **集群广播** | Redis Pub/Sub 失效广播，毫秒级同步所有节点 L1 缓存 |
| 🔄 **事务感知** | 写操作在事务 `afterCommit()` 后才失效缓存，回滚时自动清除 |
| ⚡ **延迟双删** | 内置有界线程池，处理主从同步延迟窗口（默认 200ms）|
| 🔀 **分库分表** | 与 ShardingSphere 集成，Key 自动追加分片后缀，解决跨分片污染 |
| 📊 **监控可观测** | Micrometer 指标 + `/actuator/mbcp-cache` 端点，支持实时统计、手动清除 |
| ⚙️ **双通道配置** | `application.yml` 或 `Java Config Bean`，后者优先级更高 |
| 🔧 **高扩展性** | SPI 接口：CacheKeyGenerator / CacheSerializer / CacheLock / CacheEventListener |

---

## 架构

```
┌──────────────────────────────────────────────────────────┐
│                      业务应用层                            │
└──────────────────────┬───────────────────────────────────┘
                       │ Mapper 接口调用
┌──────────────────────▼───────────────────────────────────┐
│                    MyBatis 核心                            │
│   MapperProxy → SqlSession → Executor                     │
└──────────────────────┬───────────────────────────────────┘
                       │
              ┌────────▼────────┐
              │  MBCP 拦截器    │  ← @MbcpMapper 透明模式
              │  (MbcpInterceptor)│
              └────────┬────────┘
                       │ AOP 优先（@Cacheable 等注解）
              ┌────────▼────────┐
              │  缓存切面       │  ← @Cacheable/@CacheEvict
              │  (CacheAspect)  │    /@CachePut/@Caching
              └────────┬────────┘
                       │
         ┌─────────────▼──────────────┐
         │     一致性协调器            │
         │  (ConsistencyCoordinator)  │
         │  Level A / B / C / D 路由  │
         └──────┬──────────────┬──────┘
                │              │
    ┌───────────▼──┐    ┌───────▼──────────┐
    │  Caffeine L1  │    │    Redis L2       │
    │  (本地缓存)   │◄──►│  (Redisson RMapCache)│
    └───────────────┘    └──────────────────┘
                │              │
                └──────┬───────┘
                       │
              ┌────────▼────────┐
              │   数据库 (DB)   │
              └─────────────────┘

集群节点间：Redis Pub/Sub 广播 → 所有节点 L1 同步失效
```

---

## 模块结构

```
myBatis-cache-plus/
├── pom.xml                       # 父聚合 pom（Java 21 + Spring Boot 3.3.0）
│
├── mbcp-annotation/              # 注解 + 枚举（零依赖）
│   └── @Cacheable / @CacheEvict / @CachePut / @Caching
│       @MbcpMapper / @TableHint
│       ConsistencyLevel / CacheType / EvictScope / ExpireStrategy
│
├── mbcp-cache-api/               # SPI 接口层
│   └── CacheProvider / CacheKeyGenerator / CacheSerializer
│       CacheLock / CacheEventListener / CacheLoader
│       CacheEntry / CacheStats / NullValue / ShardInfoExtractor
│
├── mbcp-cache-local/             # Caffeine L1 实现
│   └── CaffeineProvider（per-entry TTL + maximumWeight + recordStats）
│       ObjectSizeWeigher / LocalCacheProperties（双通道配置）
│
├── mbcp-cache-redis/             # Redisson L2 实现
│   └── RedissonProvider（RMapCache per-key TTL）
│       RedissonLock / RedissonReadWriteLock（Level D 专用）
│       RedisPubSubInvalidator（发布/订阅 + LRU 去重）
│       RedisStreamReplay（Level D 断线重播）
│
├── mbcp-cache-multilevel/        # L1+L2 组合 + 广播
│   └── MultiLevelCacheProvider（读：L1→L2→DB；写：双层失效）
│       InvalidationBroadcaster / RedisBroadcaster / NoOpBroadcaster
│       CircuitBreaker（CLOSED/OPEN/HALF_OPEN 熔断器）
│
├── mbcp-core/                    # 核心逻辑
│   └── interceptor/MbcpInterceptor（MyBatis 透明模式）
│       aop/CacheAspect（Spring AOP 注解驱动）
│       consistency/LevelA~DCoordinator（4 级一致性实现）
│       key/DefaultCacheKeyGenerator / ShardingAwareCacheKeyGenerator
│       protection/CacheProtector（防穿透/雪崩/击穿）
│       support/SpelExpressionEvaluator / TransactionSynchronizationSupport
│
├── mbcp-monitor/                 # 监控模块
│   └── MbcpMetrics（Micrometer 指标注册）
│       MbcpCacheEndpoint（/actuator/mbcp-cache）
│       MemoryPressureWatcher（JVM 堆压力监控，自动收缩 L1）
│       MicrometerEventListener（事件 → 指标）
│
├── mbcp-spring-boot-starter/     # 自动配置
│   └── MbcpAutoConfiguration（条件装配所有 Bean）
│       MbcpProperties（@ConfigurationProperties("mbcp")）
│       MbcpLocalCacheConfig（Java Config 接口，高优先级）
│       MbcpShardingConfiguration（ShardingSphere 适配）
│
└── mbcp-example/                 # 演示工程（H2 内存库，开箱即用）
```

---

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.hf</groupId>
    <artifactId>mbcp-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

**按需引入（HYBRID / STRONG 模式需要 Redis）：**

```xml
<!-- Redis 支持（HYBRID / Level D STRONG 一致性）-->
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>3.29.0</version>
</dependency>

<!-- 分库分表支持（可选）-->
<dependency>
    <groupId>org.apache.shardingsphere</groupId>
    <artifactId>shardingsphere-jdbc</artifactId>
    <version>5.5.1</version>
    <optional>true</optional>
</dependency>
```

### 2. 最简配置（本地缓存模式）

```yaml
mbcp:
  enabled: true
  cache-type: LOCAL       # LOCAL / REDIS / HYBRID / NONE
  default-expire: 300     # 全局默认 TTL（秒）
```

### 3. 禁用 MyBatis 原生二级缓存

```yaml
mybatis:
  configuration:
    cache-enabled: false  # 关闭 MyBatis 内置 L2，避免与 MBCP 冲突
```

### 4. 运行演示工程

```bash
git clone <repo-url>
cd myBatis-cache-plus
# 编译所有模块
/path/to/mvn clean install -DskipTests
# 启动演示工程
cd mbcp-example
/path/to/mvn spring-boot:run
```

访问 `http://localhost:8080/h2-console`（H2 控制台）验证数据写入，访问 `http://localhost:8080/actuator/mbcp-cache` 查看缓存统计。

---

## 注解使用

### `@Cacheable` — 查询缓存

```java
@Mapper
@MbcpMapper(defaultExpire = 300, autoCache = true)  // Mapper 级配置 + 透明缓存
@TableHint(tables = {"users"})
public interface UserMapper {

    // 基础用法：key 自动生成（Mapper+方法+参数哈希）
    @Cacheable(expire = 600)
    User selectById(@Param("id") Long id);

    // 自定义 SpEL key
    @Cacheable(key = "'user:' + #id", expire = 600)
    User selectByIdWithKey(@Param("id") Long id);

    // 条件缓存：age > 18 才缓存
    @Cacheable(key = "'user:age:' + #age", condition = "#age > 18", expire = 300)
    List<User> selectByAge(@Param("age") Integer age);

    // 排除结果：结果为空时不缓存
    @Cacheable(key = "'user:email:' + #email", unless = "#result == null")
    User selectByEmail(@Param("email") String email);

    // 动态开关（SpEL）
    @Cacheable(expire = 300, enabled = "#{@featureFlags.userCacheEnabled}")
    User selectByName(@Param("name") String name);
}
```

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `cacheName` | `String` | `""` | 缓存命名空间，用于批量失效；默认取 Mapper 全限定名 |
| `key` | `String` | `""` | SpEL key 表达式；空 = 自动生成 |
| `condition` | `String` | `""` | SpEL 入参条件；`false` 时跳过缓存直接查 DB |
| `unless` | `String` | `""` | SpEL 结果条件；`true` 时不缓存返回值 |
| `expire` | `long` | `0` | TTL（秒）；0 = 使用全局默认值 |
| `consistencyLevel` | `ConsistencyLevel` | `EVENTUAL` | 一致性级别（方法 > Mapper > 全局）|
| `enabled` | `String` | `"true"` | SpEL 启用开关，支持动态关闭 |

---

### `@CacheEvict` — 写后失效

```java
// 精确失效单个 key
@CacheEvict(key = "'user:' + #user.id")
int updateById(@Param("user") User user);

// 先删后执行（beforeInvocation）
@CacheEvict(key = "'user:' + #id", beforeInvocation = true)
int deleteById(@Param("id") Long id);

// 延迟双删（处理主从延迟，默认已启用）
@CacheEvict(key = "'user:' + #user.id", doubleEvict = true, doubleEvictDelayMs = 300)
int updateWithDoubleEvict(@Param("user") User user);

// 表级批量失效
@CacheEvict(scope = EvictScope.TABLE)
@TableHint(tables = {"users"})
int insertBatch(@Param("list") List<User> users);
```

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `key` | `String` | `""` | SpEL 精确 key；空时按 `scope` 决定范围 |
| `scope` | `EvictScope` | `AUTO` | 失效范围：AUTO / KEY / METHOD / TABLE / NAMESPACE |
| `beforeInvocation` | `boolean` | `false` | 方法执行前清除缓存 |
| `afterInvocation` | `boolean` | `true` | 方法执行后清除缓存 |
| `doubleEvict` | `boolean` | `true` | 延迟双删（处理主从延迟）|
| `doubleEvictDelayMs` | `long` | `0` | 第二次删除的延迟（ms）；0 = 全局配置 |

---

### `@CachePut` — Write-Through 写缓存

```java
// 执行 SQL 后将返回值写入缓存（总是执行方法体）
@CachePut(key = "'user:' + #user.id", expire = 300)
User updateAndReturn(@Param("user") User user);

// 结果为 null 时不写缓存
@CachePut(key = "'user:' + #id", unless = "#result == null")
User refreshCache(@Param("id") Long id);
```

---

### `@Caching` — 组合操作

```java
// 同时失效旧 key + 写入新 key（saveOrUpdate 语义）
@Caching(
    evict = { @CacheEvict(key = "'user:' + #user.id") },
    put   = { @CachePut(key = "'user:' + #user.id", expire = 300) }
)
User saveOrUpdate(@Param("user") User user);

// 多表联动：失效 user + order 两张表
@Caching(
    evict = {
        @CacheEvict(scope = EvictScope.TABLE),
    }
)
@TableHint(tables = {"users", "orders"})
int createUserWithOrder(User user, Order order);
```

---

### `@MbcpMapper` — Mapper 级透明缓存

```java
// autoCache=true：所有 SELECT 方法自动缓存，无需逐个加 @Cacheable
@Mapper
@MbcpMapper(
    defaultExpire = 600,
    autoCache = true,
    excludeMethods = {"selectSensitiveData"},  // 排除不缓存的方法
    consistencyLevel = ConsistencyLevel.EVENTUAL
)
public interface ProductMapper {
    Product selectById(Long id);          // 自动缓存（透明模式）
    List<Product> selectByCategory(String cat); // 自动缓存
    Product selectSensitiveData(Long id); // 被排除，不缓存
}
```

---

## 一致性级别

MBCP 提供四个级别，按场景选择最合适的一致性策略：

| 级别 | 枚举值 | 适用场景 | 多节点行为 |
|------|--------|----------|------------|
| **A** | `IGNORE` | 字典表、菜单、公告等极少变更数据 | 写操作不清缓存，完全依赖 TTL 自然过期 |
| **B** | `BEST_EFFORT` | 普通列表查询，可接受短暂脏读 | 立即清自身 L1，**异步**删 L2；其他节点 L1 靠 TTL 过期 |
| **C** | `EVENTUAL`（默认）| 绝大多数业务场景 | 同步删 L2 + **广播**清所有节点 L1（Pub/Sub）+ 事务感知 + 延迟双删 |
| **D** | `STRONG` | 库存、余额等强一致场景（需要 Redis）| Redisson **读写锁** + write-through + 广播；读操作加读锁，写操作加写锁 |

### 配置示例

```yaml
# 全局默认级别
mbcp:
  consistency-level: EVENTUAL

# Mapper 级别（注解）
@MbcpMapper(consistencyLevel = ConsistencyLevel.BEST_EFFORT)

# 方法级别（最高优先级）
@Cacheable(consistencyLevel = ConsistencyLevel.STRONG)
```

### Level C 事务感知

```java
@Transactional
public int updateUser(User user) {
    int rows = userMapper.updateById(user);
    // 缓存失效将在 Spring 事务 afterCommit() 之后执行
    // 若事务回滚，缓存也会被清除（避免脏缓存）
    return rows;
}
```

### Level D 读写锁流程

```
写操作：获取写锁 → 删 L2 → 广播 L1 → 执行 SQL → Write-through 写 L2 → 延迟双删 → 释放锁
读操作：获取读锁 → 跳过 L1（避免跨节点不一致）→ 查 L2 → miss 则查 DB + 写 L2 → 释放锁
```

> ⚠️ Level D 必须配置 Redis（Redisson），否则启动时抛出 `IllegalStateException`。

---

## 配置参考

### 完整 `application.yml`

```yaml
mbcp:
  enabled: true                        # 是否启用 MBCP（默认 true）
  cache-type: HYBRID                   # LOCAL / REDIS / HYBRID / NONE
  default-expire: 300                  # 全局默认 TTL（秒）
  consistency-level: EVENTUAL          # 全局默认一致性级别

  # ─── L1 本地缓存（Caffeine）───
  local:
    max-memory-mb: 256                 # L1 最大内存（MB）
    max-value-size-kb: 512             # 单值最大 KB，超过不缓存到 L1
    expire-after-write: 300            # 写后过期（秒）
    expire-after-access: 0             # 访问后过期（0 = 不启用）
    initial-capacity: 1000             # 初始容量估算（条目数）

  # ─── L2 分布式缓存（Redis / Redisson）───
  redis:
    host: localhost
    port: 6379
    database: 0
    password:                          # Redis 密码（可选）
    connect-timeout: 3000              # 连接超时（ms）
    timeout: 3000                      # 命令超时（ms）
    lock-wait-time-ms: 3000            # Level D 写锁等待超时（ms）
    double-evict-delay-ms: 200         # 延迟双删间隔（ms）
    l1-ttl-ratio: 0.3                  # L1 TTL = L2 TTL × 比例（HYBRID 模式）

  # ─── 缓存三大防护 ───
  protection:
    null-value-ttl-seconds: 60         # 防穿透：空值缓存 TTL（秒）
    lock-wait-time-ms: 3000            # 防击穿：互斥锁等待超时（ms）
    avalanche-offset-ratio: 0.2        # 防雪崩：随机 TTL 偏移比例（±20%）

  # ─── 失效线程池 ───
  evict:
    async-core-pool-size: 4            # 异步失效线程池核心线程数
    delayed-pool-size: 2               # 延迟双删线程池大小
    delayed-queue-capacity: 500        # 延迟双删队列容量（有界，超出丢弃）

  # ─── 分库分表（需 ShardingSphere）───
  sharding:
    enabled: false                     # 启用分片感知缓存 key 生成
    append-shard-suffix: true          # 自动追加 @db{x}t{y} 后缀
```

### 配置项速查表

| 配置键 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `mbcp.enabled` | boolean | `true` | 全局开关 |
| `mbcp.cache-type` | CacheType | `LOCAL` | 缓存类型 |
| `mbcp.default-expire` | long | `300` | 全局 TTL（秒）|
| `mbcp.consistency-level` | ConsistencyLevel | `EVENTUAL` | 全局一致性级别 |
| `mbcp.local.max-memory-mb` | long | `256` | L1 最大内存（MB）|
| `mbcp.local.max-value-size-kb` | long | `512` | 单值上限（KB）|
| `mbcp.redis.host` | String | `localhost` | Redis 主机 |
| `mbcp.redis.port` | int | `6379` | Redis 端口 |
| `mbcp.redis.double-evict-delay-ms` | long | `200` | 延迟双删间隔（ms）|
| `mbcp.redis.l1-ttl-ratio` | double | `0.3` | L1/L2 TTL 比例 |
| `mbcp.protection.null-value-ttl-seconds` | long | `60` | 防穿透空值 TTL |
| `mbcp.protection.avalanche-offset-ratio` | double | `0.2` | 防雪崩 TTL 抖动 |

---

## Java Config 双通道配置

除 `yml` 外，还可通过 Spring Bean 以编程方式配置 L1，**优先级高于 yml**：

```java
@Configuration
public class CacheConfig {

    /**
     * Java Config 优先级 > application.yml
     * 适合需要在代码中动态计算配置值的场景
     */
    @Bean
    public MbcpLocalCacheConfig customL1Config() {
        return builder -> builder
            .maxMemoryMb(512)           // 覆盖 yml 中的 max-memory-mb
            .expireAfterWrite(600)      // 覆盖 yml 中的 expire-after-write
            .initialCapacity(5000);
    }
}
```

---

## 扩展点

所有扩展接口均可通过 Spring Bean 替换默认实现：

| 接口 | 默认实现 | 用途 |
|------|----------|------|
| `CacheKeyGenerator` | `DefaultCacheKeyGenerator` | 自定义 key 生成逻辑（如加密、前缀规范）|
| `CacheSerializer` | Jackson JSON | 替换为 Kryo / Protobuf 等高性能序列化 |
| `CacheLock` | Redisson RLock | 替换为 ZooKeeper 或其他分布式锁实现 |
| `CacheEventListener` | `MicrometerEventListener` | 监听 hit/miss/evict/penetration/circuit-break 事件 |
| `CacheLoader` | 无内置 | 预热热点数据到缓存 |
| `ShardInfoExtractor` | ShardingSphere RouteContext | 自定义分片信息提取（非 ShardingSphere 场景）|

### 自定义 Key 生成器示例

```java
@Bean
public CacheKeyGenerator myKeyGenerator() {
    return (mapper, method, params, boundSql, pageInfo) -> {
        // 自定义逻辑：加统一前缀 + 业务版本号
        String base = mapper.getSimpleName() + ":" + method.getName();
        String paramHash = Hashing.murmur3_128().hashUnencodedChars(
            params.toString()).toString();
        return "v2:" + base + ":" + paramHash;
    };
}
```

### 自定义事件监听器示例

```java
@Bean
public CacheEventListener alertListener() {
    return new CacheEventListener() {
        @Override
        public void onPenetration(String key) {
            // 穿透报警：发送钉钉 / 邮件通知
            alertService.send("Cache penetration detected: " + key);
        }
        @Override
        public void onCircuitBreak(String state, String reason) {
            // Redis 熔断报警
            alertService.send("Redis circuit breaker: " + state);
        }
    };
}
```

---

## 监控与运维

### Micrometer 指标

启用 Micrometer 后（引入 `spring-boot-starter-actuator`），自动注册以下指标：

| 指标名 | 类型 | Tag | 说明 |
|--------|------|-----|------|
| `mbcp.cache.hits` | Counter | `level` (L1/L2) | 缓存命中次数 |
| `mbcp.cache.misses` | Counter | `level` | 缓存未命中次数 |
| `mbcp.cache.penetrations` | Counter | — | 穿透次数（DB 也无数据）|
| `mbcp.cache.evictions` | Counter | `cause` | 失效次数及原因 |
| `mbcp.cache.circuit_breaks` | Counter | — | Redis 熔断次数 |
| `mbcp.cache.load_time` | Timer | — | DB 加载耗时分布 |

### Actuator 端点

```yaml
management:
  endpoints:
    web:
      exposure:
        include: mbcp-cache
```

| 操作 | 请求 | 说明 |
|------|------|------|
| 查看统计 | `GET /actuator/mbcp-cache` | 返回 L1/L2 命中率、大小、Caffeine 原生统计 |
| 清除指定 key | `POST /actuator/mbcp-cache/{key}` | 精确清除 |
| 清除前缀 | `POST /actuator/mbcp-cache/{prefix*}` | 通配清除 |
| 清空全部 | `DELETE /actuator/mbcp-cache` | ⚠️ 谨慎使用 |

**响应示例：**

```json
{
  "l1": {
    "name": "caffeine-l1",
    "available": true,
    "hitCount": 1024,
    "missCount": 128,
    "hitRate": 0.889,
    "caffeine.estimatedSize": 512
  },
  "l2": {
    "name": "redisson-l2",
    "available": true,
    "hitCount": 89,
    "missCount": 39
  }
}
```

### JVM 内存压力自适应

`MemoryPressureWatcher` 每 30s 采样堆内存使用率，自动调整 L1 容量：

| 堆使用率 | 行为 |
|----------|------|
| ≥ 80% | L1 容量缩减至配置值的 50% |
| ≥ 70% | L1 容量缩减至配置值的 70% |
| < 60% | 恢复至 `mbcp.local.max-memory-mb` |

---

## 分库分表支持

当使用 **ShardingSphere** 分库分表时，同一逻辑 key（如 `user:1`）可能对应不同物理分片的数据，MBCP 通过追加分片后缀解决跨分片 key 污染问题。

### 缓存 Key 格式

```
{Mapper}:{method}:{paramHash}[@db{x}t{y}][:{pageHash}]

示例：
  UserMapper:selectById:a3f2b1c9@db0t1    ← 分库0、分表1
  UserMapper:selectById:a3f2b1c9@db1t3    ← 分库1、分表3（不同数据，不同 key）
```

### 配置启用

```yaml
mbcp:
  sharding:
    enabled: true
    append-shard-suffix: true
```

```xml
<!-- pom.xml 添加 ShardingSphere 依赖 -->
<dependency>
    <groupId>org.apache.shardingsphere</groupId>
    <artifactId>shardingsphere-jdbc</artifactId>
    <version>5.5.1</version>
</dependency>
```

### 逻辑表失效

```java
// logicalTable=true：失效时覆盖所有物理分片
@CacheEvict(scope = EvictScope.TABLE)
@TableHint(tables = {"orders"}, logicalTable = true)
int deleteOrder(Long orderId);
```

---

## 从源码构建

### 环境要求

| 工具 | 版本要求 |
|------|----------|
| JDK | 21+ |
| Maven | 3.8+ |
| Redis | 6.0+（仅 REDIS / HYBRID / STRONG 模式需要）|

### 构建命令

```bash
# 克隆仓库
git clone <repo-url>
cd myBatis-cache-plus

# 编译所有模块（多线程加速）
mvn clean compile -T 4

# 打包（跳过测试）
mvn clean package -DskipTests

# 安装到本地 Maven 仓库
mvn clean install -DskipTests

# 运行演示工程
cd mbcp-example
mvn spring-boot:run

# 仅构建特定模块
mvn clean package -pl mbcp-core -am
```

### 演示工程

启动 `mbcp-example` 后，以下端点可用：

| URL | 说明 |
|-----|------|
| `http://localhost:8080/h2-console` | H2 数据库控制台（JDBC URL: `jdbc:h2:mem:mbcp_test`）|
| `http://localhost:8080/actuator/mbcp-cache` | MBCP 缓存统计 |
| `http://localhost:8080/actuator/health` | 应用健康状态 |

---

## 设计文档

详细的架构设计、性能分析、多节点一致性推导请参阅：

- **[DESIGN.md](DESIGN.md)** — 完整设计规范（v1.1），包含：
  - 多节点一致性分析（A/B/C/D 级别在集群中的行为推导）
  - 分布式 L1 失效广播可靠性设计（Pub/Sub + Stream 重播）
  - 熔断器状态机设计
  - ShardingSphere 集成与 Seata 分布式事务协调
  - 性能目标与压测基准
  - 完整线程模型与时序图

---

## 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Java | 21 | Record, sealed class, pattern matching |
| Spring Boot | 3.3.0 | 自动配置、AOP、事务管理 |
| MyBatis | 3.0.3 | 插件拦截器基础 |
| Caffeine | 3.x | 本地缓存（L1），per-entry TTL |
| Redisson | 3.29.0 | 分布式缓存（L2）、Pub/Sub、ReadWriteLock |
| Guava | 33.2.0 | MurmurHash128 参数哈希 |
| Micrometer | Spring Boot 托管 | 可观测性指标 |
| ShardingSphere | 5.5.1 | 分库分表路由信息（可选）|

---

## 与 MyBatis-Plus 的对比

| 特性 | MyBatis-Plus | MyBatis Cache Plus |
|------|--------------|-------------------|
| 主要目标 | 简化 CRUD，增强查询 | 透明缓存，降低 DB 压力 |
| 核心机制 | 代码生成 + Wrapper | 拦截器 + 多级缓存 |
| 缓存能力 | 仅提供简单二级缓存配置 | 生产级：L1+L2+广播+防护+一致性 |
| 注解风格 | `@TableName`, `@TableId` | `@Cacheable`, `@CacheEvict` |
| 集成方式 | 替换 SqlSessionFactory | 以插件形式叠加，完全兼容原 MyBatis |
| 分布式支持 | 无 | Redis 广播 + Redisson 读写锁 |
| 一致性控制 | 无 | 4 级精细化控制 |

---

## License

```
Copyright 2024 com.hf

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
