# 08_业务与架构

## 1. 学习目标

这一阶段不只是“会写后端”，而是开始理解业务和系统如何设计。

重点：

- 支付业务
- 直播 / 社交 / 互动业务
- 系统设计
- 架构演进
- 稳定性和扩展性

## 2. 常见业务场景

### 2.1 订单系统

一个订单系统通常涉及：

- 用户下单
- 库存扣减
- 支付
- 订单状态流转

```java
public class OrderStatus {
    public static final String CREATED = "CREATED";
    public static final String PAID = "PAID";
    public static final String CANCELLED = "CANCELLED";
    public static final String FINISHED = "FINISHED";
}
```

### 2.2 用户登录

常见逻辑：

- 用户输入账号密码
- 校验密码
- 生成 Token
- 进行鉴权

```java
String token = JWT.create()
    .withClaim("userId", 1001)
    .sign(Algorithm.HMAC256("secret"));
```

## 3. 系统设计思路

系统设计时重点考虑：

- 高并发
- 数据一致性
- 扩展性
- 可靠性
- 安全性

## 4. 架构演进

典型演进路径：

1. 单体应用
2. 分层架构
3. 微服务架构
4. 云原生 / 容器化 / K8s

## 5. 复盘问题

- 订单支付中最容易出现什么问题？
- 为什么系统要拆分成模块/服务？
- 怎么保证高可用和低延迟？
- 设计一个系统时，首先看什么？

## 6. 个人建议

- 先做真实业务项目
- 再抽象系统设计
- 多看真实案例和线上问题
- 不要只停留在“会调用框架”

---

> 业务与架构阶段，是把“代码能力”升级为“系统能力”的关键时期。
