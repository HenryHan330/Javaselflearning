# 01_Java基础

## 1. 学习目标

Java 基础是后端入门的第一层。这里不要求背很多题，而是必须理解下面这些东西：

- 变量、类型、流程控制
- 面向对象：类、对象、封装、继承、多态
- 抽象类、接口、final、super
- 异常处理
- 注解
- 泛型
- 集合：List、Set、Queue、Map

## 2. 基础语法

### 2.1 for-each

for-each 适合遍历数组或集合，语法简单，代码更清晰。

```java
String[] strs = {"a", "b", "c"};
for (String str : strs) {
    System.out.println(str);
}
```

适用场景：

- 只需要遍历，不需要修改下标
- 不需要访问索引值

## 3. 面向对象

### 3.1 抽象类

抽象类不能直接实例化，通常用于定义“共性”方法，子类负责实现具体逻辑。

```java
abstract class Animal {
    public abstract void sound();
}

class Cat extends Animal {
    @Override
    public void sound() {
        System.out.println("喵喵");
    }
}
```

注意：

- 抽象类里可以有普通方法
- 抽象方法必须被子类实现
- 抽象类不能被 `new` 出来

### 3.2 接口

接口强调“规范”，不能有具体实现，类通过 `implements` 来实现接口。

```java
interface A {
    void a();
}

interface B {
    void b();
}

class X implements A, B {
    @Override
    public void a() {
        System.out.println("A");
    }

    @Override
    public void b() {
        System.out.println("B");
    }
}
```

接口和抽象类的区别：

- 抽象类：有“共性”，适合做模板
- 接口：更偏“规范”，适合约束行为

### 3.3 多态

多态的核心：父类引用指向子类对象。

```java
class Animal {
    public void sound() {
        System.out.println("动物发声");
    }
}

class Cat extends Animal {
    @Override
    public void sound() {
        System.out.println("猫叫");
    }
}

public class Demo {
    public static void main(String[] args) {
        Animal a = new Cat();
        a.sound();
    }
}
```

这里 `Animal a = new Cat();` 是典型的向上转型，运行时调用的是子类重写的方法。

### 3.4 向上转型和向下转型

```java
Animal animal = new Cat();
animal.sound();

if (animal instanceof Cat) {
    Cat cat = (Cat) animal;
    cat.sound();
}
```

关键点：

- 向上转型：`子类 -> 父类`，通常是安全的
- 向下转型：`父类 -> 子类`，需要 `instanceof` 判断
- 不要随便强转，否则会出现 `ClassCastException`

### 3.5 super

`super` 用来访问父类中的成员，常见用于构造函数和方法重写中的父类逻辑。

```java
class Parent {
    public Parent() {
        System.out.println("父类构造");
    }
}

class Child extends Parent {
    public Child() {
        super();
        System.out.println("子类构造");
    }
}
```

### 3.6 final

`final` 表示最终，不能被修改。

- `final` 变量：值不能被修改
- `final` 方法：不能被重写
- `final` 类：不能被继承

```java
final class A {
    final void test() {
        System.out.println("不能重写");
    }
}
```

### 3.7 重载和重写

#### 重载 Overload 

同一个类中，方法名相同，参数不同。返回值不作为判断标准。

```java
class MathUtil {
    int add(int a, int b) {
        return a + b;
    }

    double add(double a, double b) {
        return a + b;
    }
}
```

#### 重写 Override

子类重新实现父类方法，要求：

- 方法名相同
- 参数列表相同
- 返回类型相同或子类兼容

```java
class Parent {
    public void run() {
        System.out.println("父类跑");
    }
}

class Child extends Parent {
    @Override
    public void run() {
        System.out.println("子类跑");
    }
}
```

## 4. 异常处理

Java 里异常有两大类：

- `Error`：严重错误，通常不建议捕获
- `Exception`：程序中可处理的异常

### 4.1 try / catch / finally

```java
try {
    int a = 10 / 0;
} catch (ArithmeticException e) {
    System.out.println("除零异常：" + e.getMessage());
} finally {
    System.out.println("资源释放或收尾处理");
}
```

### 4.2 throw 和 throws

- `throw`：手动抛出异常
- `throws`：声明方法可能抛出的异常

```java
class MyException extends RuntimeException {
    public MyException(String msg) {
        super(msg);
    }
}

public class Demo {
    public static void test() {
        throw new MyException("自定义异常");
    }
}
```

### 4.3 try-with-resources

适合用来自动关闭资源，比如文件流、数据库连接等。

```java
try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.StringReader("hello"))) {
    System.out.println(br.readLine());
} catch (Exception e) {
    e.printStackTrace();
}
```

### 4.4 常见规则

1. 尽量不要捕获 `RuntimeException`
2. 尽量用 `try-with-resources` 关闭资源
3. 不要在 `finally` 里使用 `return`
4. 异常要抛给上层处理，或者自己统一处理

## 5. 注解

注解用于给代码增加元数据，常见于框架中。

```java
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface MyAnno {
    String value();
}

class Test {
    @MyAnno("注解测试")
    public void hello() {
        System.out.println("hello");
    }
}
```

常见注解：

- `@Retention`：注解保留策略
- `@Target`：注解作用目标
- `@Deprecated`：已过时
- `@SuppressWarnings`：压制警告
- `@Documented`：文档化展示

这些注解在 Spring、MyBatis、JUnit 等框架里非常常见。

## 6. 泛型

泛型可以让代码更通用，也能增强类型安全。

### 6.1 泛型类

```java
class Box<T> {
    private T value;

    public void set(T value) {
        this.value = value;
    }

    public T get() {
        return value;
    }
}
```

### 6.2 泛型方法

```java
public static <T> T first(List<T> list) {
    return list.get(0);
}
```

### 6.3 extends 和 super

- `? extends T`：表示是 `T` 的子类，读取数据安全，写入不安全
- `? super T`：表示是 `T` 的父类，写入安全，读取不安全

```java
List<? extends Number> list1 = new ArrayList<Integer>();
List<? super Integer> list2 = new ArrayList<Number>();
```

### 6.4 泛型的理解要点

Java 泛型在编译期有效，运行时会擦除类型信息。也就是说：

- 泛型主要用于编译期检查
- 运行时不保留具体类型
- 这就是泛型设计的本质

## 7. 集合框架

集合是 Java 中非常重要的基础知识。常见类型：

### 7.1 List

有序、可重复，适合按顺序存储数据。

```java
List<String> names = new ArrayList<>();
names.add("Tom");
names.add("Jerry");
System.out.println(names.get(0));
```

常见实现：

- `ArrayList`：查询快，增删慢
- `LinkedList`：增删快，查询慢

### 7.2 Set

无序、不重复，适合去重。

```java
Set<Integer> set = new HashSet<>();
set.add(1);
set.add(2);
set.add(1);
System.out.println(set.size());
```

常见实现：

- `HashSet`：无序，效率高
- `LinkedHashSet`：有序
- `TreeSet`：天然排序

### 7.3 Queue

队列，适合先进先出场景。

```java
Queue<String> queue = new LinkedList<>();
queue.offer("A");
queue.offer("B");
System.out.println(queue.poll());
```

### 7.4 Map

Map 是键值对结构，最常见的就是 `HashMap`。

```java
Map<String, Integer> scoreMap = new HashMap<>();
scoreMap.put("Java", 90);
scoreMap.put("MySQL", 88);
System.out.println(scoreMap.get("Java"));
```

Map 的常见用途：

- 用户信息缓存
- 配置项管理
- 统计某个 key 对应的值


## 8. 专题笔记

- [JVM 专题笔记](JVM/README.md)：JVM 原理、内存结构、类加载、GC、监控工具与实战排查。

> 这部分内容是 Java 后端最核心的基础能力，理解之后后面的 Spring、数据库、分布式都会更容易。
