# 07_工程实践

## 1. 学习目标

后端工程实践不只是写代码，也包括：

- Git 协作
- Maven/Gradle
- 单元测试
- 代码审查
- CI/CD
- 日志和监控
- AI 编程工具

## 2. Git 与协作

```bash
git init
git add .
git commit -m "feat: init backend project"
git branch -M main
git push origin main
```

### 常见分支策略

- feature 分支：开发功能
- develop：开发主线
- release：发布分支
- hotfix：线上修复

## 3. Maven / Gradle

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
</dependencies>
```

这些工具帮助管理依赖、打包、构建项目。

## 4. 单元测试

```java
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MathUtilTest {
    @Test
    void addShouldReturnSum() {
        assertEquals(5, 2 + 3);
    }
}
```

测试的意义：

- 确认功能正常
- 防止回归
- 提高代码稳定性

## 5. CI/CD

常见流程：

- 提交代码
- 触发自动构建
- 运行测试
- 代码检查
- 自动部署

## 6. 监控与日志

重点关注：

- 日志输出
- 链路追踪
- 错误告警
- 资源指标

## 7. AI 编程工具

AI 工具能帮助你：

- 生成模板代码
- 快速定位问题
- 解释复杂逻辑
- 生成测试代码

不过要注意：

- 不能盲目信任 AI 输出
- 必须自己检查语义和逻辑
- 代码审查仍然是核心能力

## 8. 复盘问题

- Git merge 和 rebase 的区别是什么？
- 为什么要写单元测试？
- 日志和监控的核心价值是什么？
- AI 生成代码的风险有哪些？

---

> 工程实践决定一个程序员是不是能“稳定交付”，而不是只会在本地跑起来。
