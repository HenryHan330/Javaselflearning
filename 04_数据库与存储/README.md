# 04_数据库与存储

## 1. 学习目标

数据库是后端最核心的能力之一。无论是电商、支付、论坛，还是管理系统，几乎都离不开数据库。

重点包括：

- MySQL 基础
- 索引设计
- 事务与隔离级别
- 锁机制
- SQL 优化
- Redis 基础
- 缓存使用场景

## 2. MySQL

### 2.1 表设计

```sql
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) UNIQUE,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### 2.2 索引

```sql
CREATE INDEX idx_users_email ON users(email);
```

索引可以提升查询速度，但也不是越多越好，维护成本也会增加。

### 2.3 事务

```sql
START TRANSACTION;

UPDATE account SET balance = balance - 100 WHERE id = 1;
UPDATE account SET balance = balance + 100 WHERE id = 2;

COMMIT;
```

如果中间报错，可以 `ROLLBACK`。

## 3. Redis

Redis 是高性能缓存和消息中间件。常见用于：

- 热点数据缓存
- 分布式锁
- 限流
- 消息队列

```java
// 伪代码：缓存用户信息
String key = "user:1001";
String value = redis.get(key);
if (value == null) {
    value = queryUserFromDB(1001);
    redis.set(key, value, 60);
}
```

## 4. 学习重点

- 会写 SQL，能解释执行计划
- 知道索引失效场景
- 理解事务和隔离级别
- 了解缓存与数据库一致性问题

## 5. 复盘问题

- `WHERE` 条件和索引字段的关系是什么？
- MySQL 中什么是回表？
- 事务隔离级别有哪些？
- Redis 缓存击穿、雪崩、穿透分别怎么处理？

## 6. 下一步建议

继续学习：

- Spring
- MyBatis
- Spring Boot
- 分布式缓存和消息队列

---

> 数据库能力是后端开发能否做出稳定系统的关键能力之一。
