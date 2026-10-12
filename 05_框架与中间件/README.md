# 05_框架与中间件

## 1. 学习目标

框架阶段的重点不是“背 API”，而是理解框架是如何帮你管理对象、处理请求和集成中间件的。

重点：

- Spring
- Spring Boot
- MyBatis
- AOP 和 IOC
- 自动配置
- 事务传播

## 2. Spring 核心思想

### 2.1 IoC

IoC（控制反转）表示对象的创建和管理交给容器，程序不需要自己 new。

```java
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class IoCDemo {
    public static void main(String[] args) {
        ApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        UserService userService = context.getBean(UserService.class);
        userService.sayHello();
    }
}
```

```java
import org.springframework.stereotype.Service;

@Service
public class UserService {
    public void sayHello() {
        System.out.println("hello from Spring");
    }
}
```

### 2.2 AOP

AOP 的核心是把公共逻辑抽出来，如日志、权限验证、事务。

```java
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LogAspect {
    @Before("execution(* com.example.*Service.*(..))")
    public void beforeMethod() {
        System.out.println("记录日志");
    }
}
```

## 3. MyBatis

MyBatis 主要负责数据库访问。核心思路：

- XML 或注解配置 SQL
- Mapper 映射接口
- 自动封装结果

```java
public interface UserMapper {
    User selectById(Long id);
}
```

```xml
<select id="selectById" parameterType="long" resultType="com.example.User">
    SELECT * FROM users WHERE id = #{id}
</select>
```

## 4. 学习建议

- Spring 先理解 Bean 生命周期和容器
- Spring Boot 重点看自动配置和 Starter
- MyBatis 要知道 SQL 是怎么映射到对象上的

## 5. 复盘问题

- IoC 和 DI 的关系是什么？
- AOP 解决了什么问题？
- Spring Boot 自动配置的本质是什么？
- 事务传播是如何影响多个方法之间调用的？

## 6. 下一步建议

继续深入：

- 分布式理论
- 微服务架构
- API 网关
- 注册中心和配置中心

---

> 框架阶段更重要的是“理解设计思想”，而不是只会调用接口。
