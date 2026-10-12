# 02_Java进阶

## 1. 学习目标

这一部分重点是“懂原理、会排查问题”。如果基础阶段是“会写”，进阶阶段就是“知道为什么这样写”。

核心内容：

- 并发编程
- 线程池
- 锁和 AQS
- 并发容器（阻塞/非阻塞队列、CopyOnWrite）
- ThreadLocal（线程隔离）
- JVM 内存结构
- GC
- 集合源码
- 反射和注解
- Lambda 与 Stream
- I/O 与 NIO（BIO / NIO / AIO）

## 2. 全篇脉络速览（导航）

> 内容看着多，其实只有一条主线 + 一个顺带的分支。复习时先看这一页，再往下钻。

**主线：线程怎么跑起来 → 共享数据会出什么问题 → 用什么手段治它 → 用 JDK 现成的工具**

| 板块 | 位置 | 骨架 |
|---|---|---|
| ① 线程基础：怎么让代码并发跑起来 | 2.1 | 创建 → 控制 → 拿结果 → 线程池参数与调优 → 定时任务 / Fork/Join |
| ② 同步机制：怎么保证并发不出错 | 2.2 | 问题（三大性质）→ 手段（锁 / CAS / 等待唤醒）→ 底座（AQS） |
| ③ 并发容器：拿来就用 | 2.3 / 2.4 | 要等 → 阻塞队列；不等 → 非阻塞队列；读多写少 → CopyOnWrite；Map → ConcurrentHashMap |
| ④ 非并发（顺带的分支） | 2.5–2.9 | JVM 认知、反射、Lambda、Stream、I/O（BIO / NIO） |

**② 是最容易看乱的一节，拆成三层就顺了：**

- **问题层（为什么需要同步）**：原子性 / 可见性 / 有序性、JMM、happens-before、指令重排序、死锁 / 活锁 / 饥饿；
- **手段层（怎么解决）**：`synchronized`（含锁升级）、`volatile`、CAS（ABA 问题）、原子操作类（`Atomic*` / `LongAdder`）、`ReentrantLock`、`wait/notify` 与 `Condition`、`LockSupport`、通信工具类（`CountDownLatch` / `CyclicBarrier` / `Semaphore`）、`ThreadLocal`（不共享，从根上避免竞争）；
- **框架层（抽象成底座）**：AQS = `volatile int state` + FIFO 队列 + 模板方法，`ReentrantLock` / `Semaphore` / `CountDownLatch` 都建在它上面。

**随时能查的三张「选型卡」**（哪句看不懂就顺着往下读解释）

先统一几个词：

- **背压**：消费追不上生产时，用「让生产者等待」的方式给它刹车，防止数据越堆越多、最后撑爆内存。
- **互斥**：同一时刻只允许一个线程进入某段代码（这段代码叫**临界区**），别的线程得等着。
- **公平**：多个线程排队抢锁时严格「先来先得」，不允许插队。

**卡 1：队列怎么选？**（多线程之间传数据用什么）

| 你的需求 | 选谁 | 为什么 / 什么效果 |
|---|---|---|
| 消费方追不上时，希望**自动刹车**（要背压） | `BlockingQueue`（如 `LinkedBlockingQueue`） | 队满时 `put` 会**阻塞**生产者、队空时 `take` 会阻塞消费者，天然限速、防止堆积 |
| 只求**高并发地传**，不需要等待 | `ConcurrentLinkedQueue` | 无锁（CAS），谁都不等，吞吐高；但它**不会刹车**，生产太快会一直堆到 **OOM**（内存耗尽） |

> 一句话：**需要「满了就等」→ `BlockingQueue`；不需要等、只要快 → `ConcurrentLinkedQueue`。**

**卡 2：并发容器怎么选？**（多线程读写集合用什么）

| 你的场景 | 选谁 | 为什么 |
|---|---|---|
| 要 **key-value**（键值对）映射，且多线程读写 | `ConcurrentHashMap` | 桶级锁 + CAS，并发读写都快（见 2.4） |
| 要 List / Set，且**读特别多、写极少**（监听器、白名单） | `CopyOnWriteArrayList` / `CopyOnWriteArraySet` | 写时复制，**读不加锁**；但写多会拖垮内存和性能 |
| 要 List，且**写也不少** | `Collections.synchronizedList(...)` 或自己加锁 | 读写都要抢锁，但不会像 CopyOnWrite 那样「每次写都复制整个容器」 |

> 一句话：**Map → `ConcurrentHashMap`；List / Set 且读多写少 → `CopyOnWrite`。**

**卡 3：锁怎么选？**（同一份数据要互斥访问时）

| 你的需求 | 选谁 | 为什么 |
|---|---|---|
| 只要**简单互斥**，够用就行 | `synchronized` | 写法最简单（一个关键字），JVM 自动加锁 / 解锁，还有**锁升级**优化 |
| 需要**可中断等待 / 超时 / 公平**，或一个锁配多个等待队列 | `ReentrantLock` | 能 `lockInterruptibly()`（等待时可被打断）、`tryLock(超时)`（最多等多久）、构造时选公平模式、用 `newCondition()` 拆多个等待队列 |

> 一句话：**没特殊要求 → `synchronized`；要「中断 / 超时 / 公平 / 多条件队列」→ `ReentrantLock`。**

## 3. 关键概念

### 2.1 线程和线程池

-线程是进程的子任务，是cpu调度的基本单位
不同的进程数据很难共享
同进程下的不同线程之间数据很容易共享
进程使⽤内存地址可以限定使⽤量

#### 2.1.1 什么是 `Thread`

`Thread` 是 Java 里代表「线程」的类（`java.lang.Thread`），是对底层操作系统线程的一层封装。

- **线程**：进程里的一个执行单元，是 CPU 调度的基本单位；一个进程里可以同时跑多个线程。
- **`Thread` 类**：创建它的实例并调用 `start()`，JVM 才会真正开一条新线程去执行 `run()` 里的代码。
- **`Thread` 和 `Runnable` 的关系**：`Thread` 是「线程本身」，`Runnable` 是「线程要干的活」。构造 `Thread` 时可以把 `Runnable` 传进去：

- **和普通类的区别**：普通类调方法是「当前这条线程」在执行；`Thread` 调 `start()` 后，代码是在**另一条新线程**里跑。

#### 2.1.2 创建线程的几种方式

**一句话理解**：Java 里「创建线程」本质就是给线程一个要执行的 `run()` 方法，区别只在于这个 `run()` 写在哪儿、怎么交给线程。

主流有 4 种（前 3 种是「怎么定义任务」，第 4 种是「怎么跑任务」，实际开发首选第 4 种）：

**① 继承 `Thread`，重写 `run()`**

```java
public class MyThread extends Thread {
    @Override
    public void run() {
        System.out.println("我是继承 Thread 的线程");
    }

    public static void main(String[] args) {
        new MyThread().start(); // 注意是 start()，不是 run()
    }
}
```

缺点：Java 单继承，继承了 `Thread` 就不能再继承别的类。

**② 实现 `Runnable`，交给 `Thread` 执行（更常用）**

```java
public class RunnableDemo {
    public static void main(String[] args) {
        Runnable task = () -> System.out.println("我是实现 Runnable 的任务");
        new Thread(task).start();
    }
}
```

优点：任务和线程解耦，可以实现多个接口，也能配合线程池使用。

**③ 实现 `Callable` + `FutureTask`（需要返回值 / 抛异常时）**

`Runnable` 的 `run()` 没有返回值；`Callable` 的 `call()` 有返回值，还能抛异常。

```java
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

public class CallableDemo {
    public static void main(String[] args) throws Exception {
        Callable<Integer> task = () -> {
            Thread.sleep(100);
            return 1 + 1;
        };

        FutureTask<Integer> future = new FutureTask<>(task);
        new Thread(future).start();

        System.out.println(future.get()); // 2，get() 会阻塞到任务算完
    }
}
```

**④ 使用线程池 `ExecutorService`（推荐，项目中实际用这个）**

线程创建/销毁开销大，线程池可以复用线程、控制并发数量。业务代码里几乎不直接 `new Thread()`。

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExecutorDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(() -> System.out.println("在线程池里跑的任务"));
        pool.shutdown();
    }
}
```

补充：Java 21 起还有**虚拟线程**，用 `Executors.newVirtualThreadPerTaskExecutor()` 或 `Thread.ofVirtual().start(...)` 创建，适合大量 IO 密集任务，但原理和用法与上面的平台线程不同。

**几个容易踩的坑**：

- `start()` 才是真正启动新线程，`run()` 只是普通方法调用，会在当前线程里跑。
- 同一个 `Thread` 对象只能 `start()` 一次，重复调用会抛 `IllegalStateException`。
- `Runnable` 无返回值、不抛受检异常；`Callable` 有返回值、可抛异常，靠 `Future` 拿结果。

下面看线程池的最小用法：

```java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadPoolDemo {
    public static void main(String[] args) {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        for (int i = 0; i < 5; i++) {
            final int taskId = i;
            pool.submit(() -> {
                System.out.println("执行任务：" + taskId);
            });
        }
        pool.shutdown();
    }
}
```

#### 2.1.3 控制线程的常用方法

**一句话理解**：`sleep` 让线程「歇一会儿」，`join` 让线程「等另一条线程结束」，守护线程则是「主线程一走就跟着结束的后台线程」。

**① `Thread.sleep(毫秒)`：让当前线程暂停一段时间**

```java
public class SleepDemo {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("开始");
        Thread.sleep(1000); // 当前线程暂停 1 秒（1000 毫秒）
        System.out.println("1 秒后继续");
    }
}
```

要点：
- `sleep` 是停在**当前线程**，不会释放锁（这点和 `wait()` 不同）。
- 可能抛 `InterruptedException`，要处理（`throws` 或 `try-catch`）。

**② `thread.join()`：等待另一条线程执行完再继续**

```java
public class JoinDemo {
    public static void main(String[] args) throws InterruptedException {
        Thread t = new Thread(() -> {
            for (int i = 1; i <= 3; i++) {
                System.out.println("子线程：" + i);
            }
        });
        t.start();

        t.join(); // main 线程在这里等 t 跑完，才继续往下走
        System.out.println("子线程结束了，main 才继续");
    }
}
```

要点：
- `t.join()` 表示「调用方（这里是 `main`）阻塞，直到 `t` 执行完毕」。
- `join(long millis)` 可以只等指定毫秒，超时就继续，不等到底。

**③ 守护线程：`setDaemon(true)`**

```java
public class DaemonDemo {
    public static void main(String[] args) {
        Thread t = new Thread(() -> {
            while (true) {
                System.out.println("后台监控中...");
            }
        });
        t.setDaemon(true); // 必须在 start() 之前设置
        t.start();

        System.out.println("main 结束");
        // main 是唯一的用户线程，它一结束，JVM 就退出，守护线程 t 也被结束
    }
}
```

要点：
- 线程**默认是非守护线程**，要调用 `setDaemon(true)` 才会变成守护线程。
- 守护线程**不阻止 JVM 退出**：所有非守护线程（用户线程）结束后，JVM 直接退出，守护线程会自动关闭，免去了手动关闭子线程的麻烦。
- `setDaemon(true)` 必须在 `start()` 之前，否则抛 `IllegalThreadStateException`。
- 守护线程的**默认优先级比较低**。
- 典型用途：GC 线程、后台心跳/监控；**不能用来做必须完成的任务**（如写文件、提交事务）。

**三者对比**：

| 方法 | 作用 | 一句话记忆 |
|---|---|---|
| `sleep` | 当前线程暂停指定时间 | 歇一会儿 |
| `join` | 等另一条线程结束再继续 | 等它干完 |
| `setDaemon` | 设为后台线程，不阻止 JVM 退出 | 配角，主角走了就走 |

#### 2.1.4 如何获取线程的执行结果

**一句话理解**：线程的 `run()` 没有返回值，想拿结果就得用**能返回值的任务**（`Callable`）或**异步结果容器**（`Future` / `CompletableFuture`）。

常见的 4 种方式：

**① `Callable` + `Future`（最基础）**

`Callable` 的 `call()` 有返回值，提交给线程池后返回一个 `Future`，用 `future.get()` 取结果。

```java
import java.util.concurrent.*;

public class FutureDemo {
    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(1);

        Future<Integer> future = pool.submit(() -> {
            Thread.sleep(500);
            return 1 + 1;   // 有返回值
        });

        System.out.println("先干点别的...");
        Integer result = future.get(); // 阻塞，直到任务算完
        System.out.println("结果：" + result); // 2

        pool.shutdown();
    }
}
```

要点：
- `future.get()` 会**阻塞**当前线程，直到任务完成；
- `get(timeout, unit)` 可设超时，避免一直卡住；`isDone()` 可判断是否已完成。

**② `FutureTask`（不用线程池也能用）**

`FutureTask` 既是 `Runnable` 又是 `Future`，可以直接交给 `Thread`：

```java
import java.util.concurrent.FutureTask;

FutureTask<Integer> task = new FutureTask<>(() -> 1 + 1);
new Thread(task).start();
System.out.println(task.get()); // 2
```

**③ `CompletableFuture`（推荐，能组合、能异步回调）**

Java 8 起，不想被 `get()` 阻塞就用它，通过回调拿结果：

```java
import java.util.concurrent.CompletableFuture;

public class CompletableFutureDemo {
    public static void main(String[] args) {
        CompletableFuture.supplyAsync(() -> {
            return 1 + 1;
        }).thenAccept(result -> System.out.println("结果：" + result)); // 2

        // 主线程可以做别的，结果算好后自动回调
    }
}
```

**④ `CompletionService`（批量提交，谁先完成先拿谁）**

提交一堆任务后，按「完成的先后顺序」取结果，而不是按提交顺序：

```java
import java.util.concurrent.*;

ExecutorService pool = Executors.newFixedThreadPool(3);
CompletionService<String> cs = new ExecutorCompletionService<>(pool);

for (int i = 0; i < 3; i++) {
    final int id = i;
    cs.submit(() -> "任务" + id + "完成");
}

for (int i = 0; i < 3; i++) {
    System.out.println(cs.take().get()); // 谁先完成谁先被取出
}
pool.shutdown();
```

**四种方式对比**：

| 方式 | 取结果方式 | 适用场景 |
|---|---|---|
| `Future` + 线程池 | `get()` 阻塞 | 单个任务、简单取结果 |
| `FutureTask` | `get()` 阻塞 | 不用线程池，直接配 `Thread` |
| `CompletableFuture` | 回调 `thenXxx()` | 异步、任务组合、不想阻塞 |
| `CompletionService` | `take().get()` | 批量提交，按完成顺序拿结果 |

**两个坑**：

1. `get()` 是阻塞的，任务里死循环会让调用方一直卡住 → 尽量用带超时的 `get` 或 `CompletableFuture`。
2. 任务里抛的异常，`get()` 会包成 `ExecutionException` 抛出，别忘了处理。

**一句话记忆**：要返回值就用 `Callable`；简单取用 `Future.get()`；不想阻塞、要组合用 `CompletableFuture`；批量拼速度用 `CompletionService`。

#### 2.1.5 线程的六种状态

Java 线程有 6 种状态（`Thread.State` 枚举），可用 `thread.getState()` 查看：

| 状态 | 含义 |
|---|---|
| `NEW` | 新建：`new Thread()` 之后、`start()` 之前 |
| `RUNNABLE` | 可运行：已启动，正在跑或等待 CPU 时间片 |
| `BLOCKED` | 阻塞：等待获取 `synchronized` 锁 |
| `WAITING` | 无限期等待：需被别人唤醒（`wait()`、`join()`） |
| `TIMED_WAITING` | 超时等待：等指定时间（`sleep(ms)`、`wait(ms)`） |
| `TERMINATED` | 终止：`run()` 执行完毕 |

状态流转（文字总结）：

- **NEW → RUNNABLE**：调用 `start()`。
- **RUNNABLE ⇄ BLOCKED**：等待 `synchronized` 锁时进入 `BLOCKED`，拿到锁后回到 `RUNNABLE`。
- **RUNNABLE → WAITING**：调用 `Object.wait()`、`Thread.join()`、`LockSupport.park()` 进入无限期等待；被 `notify()` / `notifyAll()` / `unpark(Thread)` 唤醒后回到 `RUNNABLE`。
- **RUNNABLE → TIMED_WAITING**：调用 `Thread.sleep(long)`、`Object.wait(long)`、`Thread.join(long)`、`LockSupport.parkNanos()`、`LockSupport.parkUntil()` 进入超时等待；等时间到或被唤醒后回到 `RUNNABLE`。
- **RUNNABLE → TERMINATED**：`run()` 执行结束。

> 补充：`RUNNABLE` 包含了操作系统的 ready（就绪）和 running（运行中）两种状态。

#### 2.1.6 线程中断的几个方法

`Thread` 类里关于线程中断的几个方法：

- **`Thread.interrupt()`**：中断线程。这里的「中断」并不会立即停止线程，而是把线程的**中断标志设为 `true`**（默认是 `false`），具体怎么响应由线程自己决定。
- **`Thread.interrupted()`（静态方法）**：测试**当前线程**是否被中断，同时会**清除**中断标志。也就是说：被中断后再调用它，第一次返回 `true` 并把标志重置为 `false`，第二次再调用就返回 `false`。
- **`thread.isInterrupted()`（实例方法）**：测试指定线程是否被中断，**不会影响**线程的中断状态（可重复调用，结果不变）。

> 记忆：`interrupt()` 是「设置标志」；`interrupted()`（静态、无参）是「查询并清除」；`isInterrupted()`（实例）是「只查询、不清除」。

#### 2.1.7 线程优先级与调度

- 线程优先级可以指定，范围是 **1~10**，默认是 **5**。
- 常量：`Thread.MIN_PRIORITY` = 1，`Thread.NORM_PRIORITY` = 5，`Thread.MAX_PRIORITY` = 10。
- 用 `Thread.setPriority(int)` 设置、`getPriority()` 获取，且要在**线程被调用（`start()`）之前**设定。
- 并不是所有操作系统都支持 10 级优先级的划分（有的只支持低、中、高 3 级）。Java 给的只是**参考值**，线程在操作系统中的最终优先级仍由操作系统决定。
- 真正的调用顺序由操作系统的**线程调度算法**决定。Java 提供了线程调度器来监视和控制处于 `RUNNABLE` 状态的线程。
- 调度策略是**抢占式**：通常情况下，高优先级的线程比低优先级的有更高概率优先执行；**优先级相同时按「先到先得」**。
- 每个 Java 程序都有一个默认的主线程，即 JVM 启动的第一个线程 —— `main` 线程。

#### 2.1.8 线程池核心参数（`ThreadPoolExecutor`）

**一句话理解**：`ThreadPoolExecutor` 用一整套参数来定义「线程池有多大、任务多了往哪儿放、放不下怎么办」。

| 参数 | 含义 |
|---|---|
| `corePoolSize` | **核心线程数（上限）**：核心线程的数量上限。注意线程池**不会一次建满**，而是每来一个任务、只要**当前线程数**没到它就新建一个，直到凑满 |
| `maximumPoolSize` | **最大线程数**：线程池允许创建的最大线程数 |
| `keepAliveTime` | **存活时间**：超出 `corePoolSize` 后创建的非核心线程，空闲多久会被回收；若开启了 `allowCoreThreadTimeOut`，则所有线程都受它管（所以说是「取决于配置」） |
| `unit` | `keepAliveTime` 的**时间单位**（如秒、毫秒） |
| `workQueue` | **任务队列**：一个**阻塞队列**，当**当前线程数达到 `corePoolSize`** 后，新任务会存进这里排队 |
| `threadFactory` | **线程工厂**：线程池内部创建线程时用它（可自定义线程名、优先级、是否守护线程） |
| `handler` | **拒绝策略**：当**队列已满、且线程数达到最大线程数**时，调用它来处理放不下的任务 |

> 一句话记忆：**核心线程 → 队列 → 最大线程 → 拒绝** —— 新任务先交给核心线程；核心满了进队列；队列也满了才开非核心线程，直到 `maximumPoolSize`；再满就交给 `handler` 拒绝。

#### 2.1.9 任务提交后的完整执行流程

**一句话理解**：任务提交后，线程池按 **核心线程 → 队列 → 非核心线程 → 拒绝** 的顺序处理。

```text
提交任务 execute(task)
        │
        ▼
  当前线程数 < corePoolSize ? ──是──▶ 直接新建一个核心线程执行
        │否
        ▼
  任务入队 workQueue.offer() ──成功──▶ 入队排队
        │                              （若此时工作线程数为 0，补建一个线程）
        │失败（队列已满）
        ▼
  当前线程数 < maximumPoolSize ? ──是──▶ 新建非核心线程执行
        │否
        ▼
     交给拒绝策略 handler
```

> **读图提示**：判断框里的「当前线程数」指**此刻线程池里已经创建了多少个线程**，`corePoolSize` 是它的**上限**，两者不是一回事——**核心线程不是一开始就全部建好，而是「来一个任务建一个」，直到凑满 `corePoolSize`**。

**四个步骤的细节**：

1. **当前线程数还没到 `corePoolSize`**（也就是「核心线程没满」）→ 直接新建一个**核心线程**执行任务（即使别的核心线程此刻空闲，也照样新建，直到凑满 `corePoolSize`）；
2. **当前线程数已达 `corePoolSize`**（核心线程已满）→ 尝试把任务**放进任务队列**排队：
   - 入队成功：还要复查一次 —— 若线程池已**不是 RUNNING 状态**，则把任务移出队列并**拒绝**；若此时工作线程数为 0，则补建一个非核心线程来消费队列；
   - 入队失败（队列满）→ 进入第 3 步；
3. **队列已满、线程数还没到 `maximumPoolSize`** → 新建**非核心线程**执行任务；
4. **队列满、线程数也到最大** → 调用**拒绝策略**（见 2.1.10）。

> 入口是 `ThreadPoolExecutor.execute(Runnable)`；源码里就是「`addWorker(核心)` → `workQueue.offer` → `addWorker(最大)` → `reject`」这几步。

#### 2.1.10 四种拒绝策略（`RejectedExecutionHandler`）

触发时机：**任务队列已满，且线程数已达到 `maximumPoolSize`**。

| 策略 | 行为 | 特点 |
|---|---|---|
| `AbortPolicy`（**默认**） | 直接抛 `RejectedExecutionException` | 能让调用方**感知到**提交失败，可捕获处理；最安全 |
| `CallerRunsPolicy` | **谁提交、谁执行**（由提交任务的线程自己跑这个任务） | 提交者被占用 → 自然**降速**，起到背压作用，不丢任务 |
| `DiscardPolicy` | **静默丢弃**该任务 | 不抛异常、不通知 → **神不知鬼不觉丢数据**，最危险 |
| `DiscardOldestPolicy` | 丢掉**队列里排队最久**的那个任务，再重新尝试提交当前任务 | 保新弃旧 |

- 想自定义：实现 `RejectedExecutionHandler` 接口，在 `rejectedExecution(r, executor)` 里写自己的逻辑（如落库、告警）。
- **选择建议**：默认 `AbortPolicy` 最不容易埋雷；要限速/不丢任务用 `CallerRunsPolicy`；`Discard*` 系列会丢任务，除非业务明确允许，否则别用。

#### 2.1.11 线程池的五种状态

线程池内部用一个 `ctl`（一个 `AtomicInteger`）同时装两样东西：**高 3 位存状态，低 29 位存线程数**。

| 状态 | 含义 | 怎么进入 |
|---|---|---|
| `RUNNING` | 接受**新任务**，也处理**队列里的任务** | 创建后默认 |
| `SHUTDOWN` | **不收新任务**，但会**把队列里已排队的任务处理完** | 调用 `shutdown()` |
| `STOP` | **不收新任务**、**不处理队列任务**，并**中断正在执行的任务** | 调用 `shutdownNow()` |
| `TIDYING` | 所有任务已终止、工作线程数为 0，**准备执行 `terminated()` 钩子** | 上面两个状态的收尾阶段 |
| `TERMINATED` | `terminated()` 执行完毕，线程池**彻底终止** | 最终态 |

**流转关系**：

```text
RUNNING ──shutdown()──▶ SHUTDOWN ──(队列空且线程都结束)──▶ TIDYING ──▶ TERMINATED
   │                                                                      ▲
   └──shutdownNow()──▶ STOP ──(线程都结束)──────────────────────────────┘
```

**`shutdown()` 和 `shutdownNow()` 的区别**：

| | `shutdown()` | `shutdownNow()` |
|---|---|---|
| 新任务 | 拒收 | 拒收 |
| 队列里的任务 | **继续执行完** | **不再执行** |
| 正在执行的任务 | 等它跑完 | **尝试中断**（`interrupt`） |
| 返回值 | 无 | 返回**还没执行的任务列表** |

#### 2.1.12 核心线程数怎么定（CPU 密集 / IO 密集）

先分清任务类型：

- **CPU 密集型**（计算为主，如加解密、复杂运算）：线程数 ≈ **CPU 核数 + 1**。
  - `+1` 是给偶发的页缺失、其他中断留一点余量；线程再多只会增加**上下文切换**（CPU 在不同线程间来回切）的开销，反而更慢。
- **IO 密集型**（大量等待网络 / 磁盘 / 数据库，如 Web 服务）：线程数可以**远超核数**，常用经验值 ≈ **2 × 核数**。
  - 更精确的估算：**线程数 = 核数 × (1 + 平均等待时间 / 平均计算时间)** —— 等待时 CPU 是闲的，多开线程能让 CPU 去干别的活。
  ⼀般需要设置有界队列的⼤⼩，⽐如 LinkedBlockingQueue 在构造的时候可以传⼊参数来限制队列中任务数据的
  ⼤⼩，这样就不会因为⽆限往队列中扔任务导致系统的 oom

**但公式只是起点**：真实线程数要**压测调优** —— 看 CPU 利用率、QPS、响应时间、队列是否积压，再结合监控动态调整。别把公式当标准答案。

**补充：为什么不推荐用 `Executors` 建线程池**

`Executors` 的几个工厂方法都有 OOM 隐患：

- `newFixedThreadPool` / `newSingleThreadExecutor`：任务队列用**无界** `LinkedBlockingQueue` → 任务一直堆积，最后 **OOM**；
- `newCachedThreadPool` / `newScheduledThreadPool`：最大线程数是 `Integer.MAX_VALUE` → 会**无限创建线程**，同样 **OOM**。

所以推荐**手动 `new ThreadPoolExecutor(...)`**，把**队列容量**和**最大线程数**都显式写清楚。

#### 2.1.13 线程池监控（常用方法）

**一句话理解**：用几个 `getXxx` 方法就能看到线程池「有多忙、满没满、干完了多少、积压多少」，用来判断要不要调参。

| 方法 | 含义 | 用途 |
|---|---|---|
| `getPoolSize()` | 当前线程池中的**线程数量** | 看池子当前规模 |
| `getActiveCount()` | **正在执行任务**的线程数（近似值） | 看当前有多忙 |
| `getLargestPoolSize()` | 线程池**曾经创建过的最大线程数** | **判断线程是否满过**（若等于 `maximumPoolSize`，说明撑到过上限） |
| `getCompletedTaskCount()` | **已经执行完成**的任务总数 | 看处理进度 / 吞吐 |
| `getTaskCount()`（配套） | **已提交**的任务总数（≈ 已完成 + 正在执行 + 排队中） | 和 `completed` 一起算积压 |

**怎么用它判断线程池状态**：

- `getLargestPoolSize() == maximumPoolSize` → 线程池**满过**，可能要调大队列或最大线程数；
- `getActiveCount()` **长期**接近 `getPoolSize()` → 线程**长期繁忙**，考虑扩容；
- `getTaskCount() - getCompletedTaskCount()` → 大致是**还没处理完**的任务数（积压量）；队列积压也可直接看 `executor.getQueue().size()`。

> 注意：这些都是**瞬时快照 / 近似值**（并发下随时在变），要判断趋势得**持续采集 + 上报监控**（定时打印或埋点），而不是只看一眼。

> 一句话记：**`getPoolSize` 看规模、`getActiveCount` 看多忙、`getLargestPoolSize` 看满没满、`getCompletedTaskCount` 看干完了多少。**

#### 2.1.14 定时任务（`ScheduledThreadPoolExecutor`）

**一句话理解**：`ScheduledThreadPoolExecutor`（**定时任务线程池**）是 `ThreadPoolExecutor` 的子类，专门用来「**延迟多久再执行**」或「**每隔一段时间反复执行**」任务。

**两个用途**：

- 指定时间**延迟**后执行**一次**（延时任务）；
- **周期性重复**执行（定时任务）。

**三个核心方法**（都通过 `ScheduledExecutorService` 接口使用）：

| 方法 | 第一次执行 | 之后怎么排 |
|---|---|---|
| `schedule(task, delay, unit)` | 延迟 `delay` 后执行 | **只执行一次**，不重复 |
| `scheduleAtFixedRate(task, initialDelay, period, unit)` | `initialDelay` 后执行第一次 | 以「**上次开始执行**的时刻」为基准，每隔 `period` 触发一次（**固定速率**） |
| `scheduleWithFixedDelay(task, initialDelay, delay, unit)` | `initialDelay` 后执行第一次 | 以「**上次执行结束**的时刻」为基准，再等 `delay` 才执行（**固定延时**） |

**两者最容易混的差别 ——「按开始算」还是「按结束算」**：

- `scheduleAtFixedRate`（固定速率）：周期从**上次开始**算。若某次任务执行时间 **> `period`**，下一次会**马上接着跑**（甚至连续执行、出现「追赶」），任务可能越积越多；
- `scheduleWithFixedDelay`（固定延时）：间隔从**上次结束**算。无论任务跑多久，两次之间都**保证空出 `delay`**，不会追赶。

> 一句话记：**要「每隔固定周期跑」用 `scheduleAtFixedRate`（按开始算，会追赶）；要「干完歇一会儿再跑」用 `scheduleWithFixedDelay`（按结束算，不追赶）。**

**四个坑**：

1. **周期任务一旦抛异常，后续调度会被静默终止**（再也不会跑）—— 任务体里必须用 `try-catch` 兜住，别让异常冒出去；
2. `scheduleAtFixedRate` 的任务别太耗时（执行时间超过 `period` 会**追赶 / 堆积**），单次耗时长的任务优先用 `scheduleWithFixedDelay`；
3. `Executors.newScheduledThreadPool(n)` 内部的队列是**无界**的 `DelayedWorkQueue`，任务堆积有 **OOM**（内存耗尽，`OutOfMemoryError`）风险；最大线程数也是 `Integer.MAX_VALUE`；
4. 提交后返回 `ScheduledFuture`，**周期任务要主动 `cancel()`** 才会停，否则会一直跑下去。

> 一句话记：**一次 → `schedule`；周期 → `scheduleAtFixedRate` / `scheduleWithFixedDelay`；记得 `try-catch` 兜异常、用完 `cancel`。**

#### 2.1.15 Fork/Join（分治框架）

**一句话理解**：Fork/Join 是 JDK 7 引入的**分治（divide-and-conquer）并发框架** —— 把大任务 **Fork（拆）**成小任务并行跑，再 **Join（合并）**结果。核心是 `ForkJoinPool` + `ForkJoinTask`。

- **和普通线程池的区别**：`ThreadPoolExecutor` 是**所有线程抢一个共享队列**；`ForkJoinPool` 是**每个线程一个双端队列 + 工作窃取**。

**三个核心部件**

| 部件 | 作用 |
|---|---|
| **`ForkJoinPool`** | 专用线程池（**不是** `ThreadPoolExecutor` 的子类），线程数默认 = CPU 核数 |
| **`ForkJoinTask`** | 任务抽象；常用子类 `RecursiveTask<V>`（**有返回值**）、`RecursiveAction`（**无返回值**） |
| **工作窃取（work-stealing）** | 线程自己从队列**头部**取任务（LIFO），**空闲**时去别的线程队列**尾部偷**任务（FIFO） |

> 工作窃取为什么快：头尾分开取能减少竞争，空闲线程不闲着，负载自动均衡。

**最小用法（记结构就够）**

```java
class SumTask extends RecursiveTask<Long> {
    int[] arr; int lo, hi;
    protected Long compute() {
        if (hi - lo <= 1000) {          // 基准条件：够小就直接算
            long s = 0;
            for (int i = lo; i < hi; i++) s += arr[i];
            return s;
        }
        int mid = (lo + hi) >>> 1;
        SumTask left  = new SumTask(arr, lo, mid);
        SumTask right = new SumTask(arr, mid, hi);
        left.fork();                    // 拆：异步提交左半
        long r = right.compute();       // 当前线程自己算右半
        return left.join() + r;         // 合：等左半结果再相加
    }
}
// 提交：pool.invoke(new SumTask(arr, 0, arr.length));
```

**关键方法**：`fork()`（异步提交）、`join()`（等结果并合并）、`invoke()`（提交并等待）、`compute()`（拆分逻辑）。

**选型**

| 场景 | 选谁 |
|---|---|
| **可递归拆分**的大计算（归并排序、大数组求和、批量遍历） | `ForkJoinPool` |
| 独立、不拆分、或含**阻塞 IO** 的任务 | `ThreadPoolExecutor` |
| 简单并行流 | `parallelStream`（**底层就是 `commonPool()`**） |

**五个坑**

1. **任务粒度别太细**：拆太碎，调度 + 合并的开销可能**大于**计算收益，要设**阈值（基准条件）**；
2. **别在 Fork/Join 任务里做阻塞 IO**：线程数是**固定的 CPU 核数**，一阻塞就占着线程、窃取也失效 → 阻塞型任务用 `ThreadPoolExecutor`；
3. **别拿 `ForkJoinPool.commonPool()` 跑阻塞 / IO 任务**：它是**全局共享**的（默认核数 -1），堵住会连累 `parallelStream`、`CompletableFuture`；
4. **拆分必须有出口**（base case），否则无限递归 → 栈溢出；
5. **异常会被包装**：`compute()` 抛的异常，`join()` 时以 `RuntimeException` / `ExecutionException` 抛出，要处理。

> 一句话记：**Fork/Join = 分治 + 工作窃取；大任务 `fork` 拆、小到阈值直接算、结果 `join` 合并；只适合 CPU 密集、可拆分，别塞阻塞 IO。**

### 2.2 同步问题

```java
class Counter {
    private int count = 0;

    public synchronized void add() {
        count++;
    }

    public int getCount() {
        return count;
    }
}
```

这里的 `synchronized` 表示在同一时刻只能有一个线程进入该方法，避免并发安全问题。

#### 2.2.1 原子性与可见性

- **原子性**：一个操作或多个操作，要么全部执行并且执行过程不会被任何因素打断，要么就都不执行。
- **可见性**：当多个线程访问同一个变量时，一个线程修改了这个变量的值，其他线程能够立即看到修改后的值。

**`volatile`**：为了解决多线程的**可见性**问题，Java 提供了 `volatile` 关键字。当一个共享变量被 `volatile` 修饰时，它会保证修改的值**立即更新到主存**，这样当其他线程需要读取时，就会从内存中读到新值。

```java
public class VolatileDemo {
    private volatile boolean flag = false; // 一个线程改了，其他线程能立刻看到

    public void stop() {
        flag = true;
    }

    public void work() {
        while (!flag) {
            // flag 为可见的，能及时退出循环
        }
    }
}
```

> 注意：`volatile` 只保证可见性，**不保证原子性**（比如 `count++` 依然不安全）。Java 的锁机制如 `synchronized` 和 `Lock` 既能保证可见性，也能保证原子性。

#### 2.2.2 死锁、活锁与饥饿

- **死锁（Deadlock）**：两个或多个线程互相持有对方需要的资源，并互相等待对方释放，导致谁都无法继续执行。比如线程 A 持有锁 1 等锁 2，线程 B 持有锁 2 等锁 1，双方永远等下去。
- **活锁（Livelock）**：线程没有被阻塞，但因为不断重试、不断改变状态，始终无法向前推进。就像两个人互相让路，结果谁都没能过去。
- **饥饿（Starvation）**：某个线程长期得不到它需要的资源（如 CPU、锁），一直无法执行。常见原因是优先级太低，或资源总被高优先级的线程抢走。

#### 2.2.3 Java 内存模型（JMM）与 happens-before

**JMM 是什么**：Java 内存模型（JMM）定义了 Java 程序中的变量、线程如何和**主存**以及**工作内存**进行交互的规则，主要涉及多线程环境下共享变量的**可见性**、**指令重排**等问题，是理解并发编程的关键。

- JMM 采用的是**共享内存并发模型**，它通过控制主存与每个线程的本地内存（工作内存）之间的交互，来提供**内存可见性**保证。
- JMM 定义多线程之间如何互相交互的规则，主要目的是解决由于**编译器优化、处理器优化和缓存系统**等导致的**可见性、原子性和有序性**问题。
- **`volatile`**：保证多线程操作共享变量的**可见性**，并**禁止指令重排序**（但不保证原子性）。
- **`synchronized`**：不仅保证**可见性**，同时也保证了**原子性（互斥性）**。

**happens-before 关系**：如果一个操作 happens-before 另一个操作，那么第一个操作的执行结果将对第二个操作**可见**，而且第一个操作的执行顺序排在第二个操作**之前**。

> 记忆：JMM 管的是「线程和主存之间怎么交互」；`volatile` 保「可见性 + 禁止重排」，`synchronized` 保「可见性 + 原子性」。

#### 2.2.4 指令重排序与 volatile

**为什么要重排序**：重排序是为了优化性能，但**不管怎么重排序，单线程下程序的执行结果不能被改变**。

**举例**：

```java
a = 1;
b = 2;
c = a + b;
```

- 第一步 `a = 1` 和第二步 `b = 2` 之间**不存在数据依赖关系**，所以可能会发生重排序；
- 但 `c = a + b` 这个操作**不会被重排序**，因为要保证最终结果一定是 `c = a + b = 3`。

**`volatile` 如何禁止重排序**：用 `volatile` 修饰共享变量，就可以禁止这种重排序。

**volatile 的定义（语义）**：执行到 `volatile` 变量时，其**前面的所有语句都必须执行完**，**后面的所有语句都未执行**；且前面语句的结果对 `volatile` 变量及其后面的语句**可见**。

> 记忆：重排序不能改变单线程结果（有数据依赖的不能重排）；`volatile` 像一道「屏障」，它之前的语句先执行完，之后的语句还没执行。

#### 2.2.5 synchronized 关键字

`synchronized` 可以保证在**同一时刻只有一个线程**执行某个方法或某个代码块（主要是对方法/代码块中存在**共享数据**的操作）。

另一个重要作用：`synchronized` 可保证一个线程的变化（主要是共享数据的变化）被其他线程所看到，即**保证可见性，完全可以替代 `volatile` 的功能**。

**三种应用方式**：

| 用法 | 加锁对象 |
|---|---|
| 修饰**实例方法** | 当前对象（`this`） |
| 修饰**静态方法** | 当前类的 `Class` 对象（为当前类加锁） |
| 修饰**代码块** | 指定加锁对象，对给定对象加锁 |

```java
class SyncDemo {
    // 方式一：实例方法，锁 this
    public synchronized void method1() { }

    // 方式二：静态方法，锁当前类
    public static synchronized void method2() { }

    // 方式三：代码块，锁指定对象
    private final Object lock = new Object();
    public void method3() {
        synchronized (lock) { }
    }
}
```

**可重入**：`synchronized` 是**可重入锁**，因此一个线程调用 `synchronized` 方法的同时，在其方法体内部调用该对象的另一个 `synchronized` 方法是**允许的**。

**什么叫重入**：重入 = **同一个线程**在已经持有某把锁的情况下，还能**再次成功获取这把锁**。锁的互斥是「防别的线程」，不是「防自己」。

为什么需要重入 —— 真实代码里锁很难只加一层：

```java
public class ReentrantDemo {
    public synchronized void a() {
        b();                          // 已经持有 this 的锁，又要再用它
    }

    public synchronized void b() {    // 锁的是同一个 this
        System.out.println("b");
    }
}
```

如果锁不可重入，`a()` 里调 `b()` 时线程会把自己挡在门外 —— **自己等自己释放锁，永久死锁**。递归调用同理：

```java
public synchronized void countdown(int n) {
    if (n <= 0) return;
    countdown(n - 1);        // 递归，每层都要拿同一把锁
}
```

不可重入长什么样（`StampedLock` 就是不可重入的）：

```java
StampedLock sl = new StampedLock();
long s1 = sl.writeLock();   // 成功
long s2 = sl.writeLock();   // 死锁，永久阻塞
```

**重入是怎么实现的** —— 记住「**锁归哪个线程** + **一个计数器**」：

- **`synchronized`**：靠对象头 monitor 里的 `owner`（持有锁的线程）和 `recursions`（重入次数）；每进一次同步块 +1，每退一次 -1，**减到 0 才真正释放**，别的线程才能抢。
- **`ReentrantLock`**：靠 AQS 的 `state`（重入次数）和 `exclusiveOwnerThread`（持有者）。

```java
// 简化后的 tryAcquire 逻辑
if (state == 0) {                     // 没人占，直接抢
    casState(0, 1); setOwner(current);
} else if (getOwner() == current) {   // 占的人是我自己 → 重入
    state++;
} else {
    return false;                     // 别人占着，去排队
}
```

**三个常见的坑**：

1. `lock()` 两次只 `unlock()` 一次 → 锁根本没释放，其他线程永远阻塞（线上最难查的一类问题）。
2. `unlock()` 次数多了 → 抛 `IllegalMonitorStateException`（当前线程已不是锁的持有者）。
3. 可重入只对**同一线程**有效 → 换个线程照样互斥；而且计数**按锁对象**算，A 锁的重入和 B 锁无关。

> 记忆：重入 = 「锁认人」—— 同一线程再拿同一把锁，允许，只加个计数；**计数归零才算真正释放**（所以 `lock()` 几次就要 `unlock()` 几次）。

> 记忆：`synchronized` = 互斥（原子性）+ 可见性；三种用法分别锁「this / 类 / 指定对象」，且可重入。

#### 2.2.6 锁升级（锁膨胀）

**一句话理解**：为了让 `synchronized` 在不同竞争程度下都有合适的开销，JDK 6 起 HotSpot 会根据**竞争的激烈程度**让锁逐级升级：**无锁 → 偏向锁 → 轻量级锁 → 重量级锁**，这个过程叫「锁膨胀」。锁**只能升级，不能降级**。

**锁状态存在哪**：记录在对象的**对象头（Mark Word）**里，用最低几位标记当前是哪种锁，同时记录偏向的线程 ID、指向栈中锁记录的指针等。

| 锁状态 | 优点 | 缺点 | 适用场景 |
| 无锁 | 不加锁，没有线程挂起/唤醒的开销 | 修改失败要不断重试，一直失败会浪费 CPU | 冲突很少，同一时刻只有一个线程能修改成功的场景（如 CAS） |
| 偏向锁 | 加锁和解锁不需要额外的消耗，和执行非同步方法比仅存在纳秒级的差距 | 如果线程间存在锁竞争，会带来额外的锁撤销的消耗 | 适用于只有一个线程访问同步块的场景 |
| 轻量级锁 | 竞争的线程不会阻塞，提高了程序的响应速度 | 如果始终得不到锁竞争的线程使用自旋会消耗 CPU | 追求响应时间，同步块执行速度非常快 |
| 重量级锁 | 线程竞争不使用自旋，不会消耗 CPU | 线程阻塞，响应时间缓慢 | 追求吞吐量，同步块执行时间较长 |

**升级过程（文字总结）**：

- **无锁**：不加锁，靠 CAS 尝试修改，失败就重试。
- **无锁 → 偏向锁**：第一个线程进入同步块时，用 CAS 把**自己的线程 ID** 记到 Mark Word 里。之后同一个线程再进来，只要比对一下 ID 就直接通过，连 CAS 都省了 —— 这就是「偏向」。
- **偏向锁 → 轻量级锁**：一旦**另一个线程**来竞争，偏向锁就要**撤销**（撤销本身是有消耗的，且必须在安全点进行），升级为轻量级锁：线程在自己的栈帧里建锁记录，用 CAS 抢 Mark Word，抢不到就**自旋**等待。
- **轻量级锁 → 重量级锁**：自旋超过一定次数（自适应自旋：由 JVM 根据历史成功率决定）还是拿不到锁，就升级为**重量级锁**，线程真正被**阻塞挂起**，进入 monitor 的等待队列，由操作系统调度唤醒。

> 记忆：偏向锁「偏心」一个线程，轻量级锁「自旋」抢锁，重量级锁直接「阻塞」排队。
>
> 版本提示：JDK 15 起偏向锁默认被禁用（JEP 374），实际项目里主要是 无锁 / 轻量级锁 / 重量级锁 这条路径。

#### 2.2.7 悲观锁与乐观锁

**一句话理解**：悲观锁是「先加锁，再操作」，乐观锁是「先操作，冲突了再用 CAS 重试」。

**悲观锁**

对于悲观锁来说，它总是认为每次访问共享资源时会发生冲突，所以必须对每次数据操作加上锁，以保证临界区的程序同一时间只能有一个线程在执行。

**乐观锁**

乐观锁，顾名思义，它是乐观派。乐观锁总是假设对共享资源的访问没有冲突，线程可以不停地执行，无需加锁也无需等待。一旦多个线程发生冲突，乐观锁通常使用一种称为 CAS 的技术来保证线程执行的安全性。

**两者对比**：

| 对比项 | 悲观锁 | 乐观锁 |
|---|---|---|
| 核心思想 | 假设一定会冲突，所以先加锁 | 假设不会冲突，先执行，冲突了再补救 |
| 是否加锁 | 每次操作都加锁 | 不加锁，也不等待 |
| 冲突处理 | 抢不到锁就阻塞等待 | 用 CAS 检测，失败就重试或放弃 |
| 适用场景 | 写多、竞争激烈 | 读多写少、冲突少 |

#### 2.2.8 什么是 CAS

CAS是一种无锁算法，在 CAS 中，有这样三个值：

- **V**：要更新的变量（var）
- **E**：预期值（expected）
- **N**：新值（new）

判断 V 是否等于 E，如果等于，将 V 的值设置为 N；如果不等，说明已经有其它线程更新了 V，于是当前线程放弃更新，什么都不做。这里的预期值 E 本质上指的是「旧值」。

当多个线程同时使用 CAS 操作一个变量时，只有**一个会胜出**并成功更新，其余均会失败，但**失败的线程并不会被挂起**，仅是被告知失败，并且**允许再次尝试**（这个「再次尝试」就是自旋）。

> 记忆：悲观锁「先锁后干」，乐观锁「先干再说，冲突了用 CAS 重试」；CAS 就是「比较并交换（Compare And Swap）」，靠 V / E / N 三个值判断，失败不挂起、可重试。

**补充（面试常问的 CAS 三个问题）**：

- **ABA 问题**：V 从 A 改成 B 又改回 A，CAS 检查不出来（以为没人动过）→ 用**版本号**或 `AtomicStampedReference` 解决。
- **自旋开销大**：一直失败就会一直重试，长时间自旋会消耗 CPU。
- **只能保证一个变量的原子性**：要同时对多个变量做 CAS，可以把它们**封装成一个对象**（如 `AtomicReference`）或改用锁。

#### 2.2.9 原子操作类（`Atomic`）

**一句话理解**：`java.util.concurrent.atomic` 包下的一组类，**靠 CAS 把「读-改-写」三步合成一个不可分割的动作**，让你在**不加锁**的前提下保证**单个变量**的线程安全。

- **为什么需要它**：`count++` 其实是「读取 → +1 → 写回」三步，多线程下会**丢更新**（两个线程都读到 1，各自写回 2，结果少加了一次）。`synchronized` 能治，但要加锁（重）；原子类用 CAS 无锁治它，所以也叫「**无锁 / 乐观同步**」。
- **底层**：调的是 `Unsafe.compareAndSwapInt` 这类方法（原理见 2.2.8 CAS）。

```java
AtomicInteger count = new AtomicInteger(0);
count.incrementAndGet();     // 原子 +1（替代非原子的 count++）
count.compareAndSet(1, 10);  // 期望还是 1 就改成 10，否则不动
```

**按用途分 5 类（选型地图）**

| 类别 | 代表类 | 用在哪 |
|---|---|---|
| **基本类型** | `AtomicInteger`、`AtomicLong`、`AtomicBoolean` | 计数器、状态标志位 |
| **引用类型** | `AtomicReference`、`AtomicStampedReference`、`AtomicMarkableReference` | 原子更新**对象引用**；后两个带「版本号 / 布尔标记」，专治 ABA |
| **数组** | `AtomicIntegerArray`、`AtomicLongArray`、`AtomicReferenceArray` | 原子更新数组里的**某个元素** |
| **字段更新器** | `AtomicIntegerFieldUpdater` 等 | 让**已有类**的某个 `volatile` 字段获得原子性，不用改类 |
| **高并发累加器** | `LongAdder`、`DoubleAdder`、`LongAccumulator` | 高并发**计数 / 求和**，比 `AtomicLong` 快 |

**5 类逐个细讲（门道主要在 ②③④）**

**① 基本类型 —— 一个数字 / 布尔**

- 变量形态：一个 `int` / `long` / `boolean`，最常见，用来做**计数器、序号、开关标志位**。
- 「**只执行一次**」的经典用法：`AtomicBoolean` 配 `compareAndSet(false, true)`，抢到执行权的线程才干活。
- 选型：普通并发计数用它；**超高并发**计数改用 ⑤ 的 `LongAdder`。

**② 引用类型 —— 一个对象引用**

- **`AtomicReference`**：原子换引用；核心用途是把**多个字段打包成一个不可变对象**一次性替换，绕过「CAS 只能保证一个变量」的限制。
  - **坑**：`compareAndSet` 比的是**引用相等（`==`，是不是同一个对象）**，不是 `equals` —— 「值相等的两个不同对象」会被判为不相等。
- **`AtomicStampedReference`**：引用 + 一个 `int` **版本号（stamp）**，每次修改版本号 +1，CAS 要**同时**比对「引用」和「版本号」→ **ABA 问题的正解**（关心「改了几次」）。
- **`AtomicMarkableReference`**：引用 + 一个 `boolean` **标记（mark）**，只关心「**有没有被改过**」→ 典型用途是无锁链表里给节点打「已删除」标记。
- 记忆：`Stamped` = 带**次数**（版本号）；`Markable` = 只带**有没有**（布尔）。

**③ 数组 —— 数组里的某一格**

- 作用：原子更新数组的**单个元素**，如 `array.incrementAndGet(i)`、`array.compareAndSet(i, 旧值, 新值)`。
- **为什么不用 `AtomicInteger[]`？**（高频面试点）：`AtomicInteger[]` 里「**元素引用的替换**」本身**不是原子的**（换第 `i` 格的引用可能丢更新）；而 `AtomicIntegerArray` 用 `Unsafe` 直接在**数组基址 + 偏移**上做 CAS，才是真原子。
- 选型：分片 / 槽位计数、索引化的状态表。

**④ 字段更新器 —— 已有类里的一个 `volatile` 字段**

- 作用：让**已经写好、不想改**的类，其某个字段获得原子更新能力，而**不用把字段类型改成 `AtomicInteger`**（省内存、不破坏原有结构）。
- 获取：`AtomicIntegerFieldUpdater.newUpdater(MyClass.class, "fieldName")`。
- **硬性要求（坑最多的一类）**：
  1. 字段**必须是 `volatile`**；
  2. 不能是 `static`、不能是 `final`；
  3. 只能更新**本类 / 子类**、且调用方**有访问权限**的字段；
  4. 字段类型必须与更新器匹配（`int` 配 `AtomicIntegerFieldUpdater`…）。
- 选型：框架内部（Netty、AQS 的 `Node`）用它**省对象开销** —— 比每个字段都包一个 `AtomicInteger` 省内存。
- **坑**：字段名是**字符串**，写错要到**运行时**才报错，不好排查。

**⑤ 高并发累加器 —— 会被很多线程争抢的累加值**

- **`LongAdder` / `DoubleAdder`**：**高并发**计数 / 求和，专门优化 `AtomicLong` 的热点问题。
  - **原理**：`AtomicLong` 是**所有线程抢一个变量**；`LongAdder` 把值分散成 `base + Cell[]` 数组，不同线程更新**不同格子**（CAS 冲突大减），要结果时 `sum()` 再把各格**汇总**。
  - **代价**：`sum()` 得到的是**近似值 / 最终一致**（并发读的时候，别的线程可能还在写别的格子）。
- **`LongAccumulator` / `DoubleAccumulator`**：更通用，可**自定义二元运算**，不只会加法。例：`new LongAccumulator((x, y) -> Math.max(x, y), Long.MIN_VALUE)` 求最大值。
- **坑**：只适合「**只累加、最后读一次**」的场景；需要「先读后写」的**复合判断**时，它给不了原子性，得用 `AtomicLong` 或加锁。

> 选型锚：**基本 → 引用 → 数组 → 字段 → 累加器**，本质是「CAS 的目标」从『一个变量』一路扩展到『对象 / 数组格 / 别人的字段 / 高频热点』。

**核心就 4 个动作**（记住语义即可，不用背方法签名）：

- `get` / `set`：读 / 写；
- `compareAndSet(期望值, 新值)`：**CAS 本体**，成功返回 `true`；
- `getAndIncrement()` / `incrementAndGet()`：`i++` / `++i` 的原子版；
- `accumulateAndGet(x, 函数)`：「读 → 用函数算 → 写」的原子版（Java 8+，比手写 CAS 自旋更好用）。

**四个坑**：

1. **CAS 只能保证「一个变量」的原子性**：要原子地改多个字段，用 `AtomicReference` 把它们**打包成一个对象**一起换；
2. **ABA 问题**：值从 A 变成 B 又变回 A，CAS 察觉不到「中间变过」→ 用带**版本号**的 `AtomicStampedReference`（或带布尔标记的 `AtomicMarkableReference`）；
3. **高并发下 `AtomicLong` 会自旋空转、白烧 CPU**（所有线程抢同一个变量）：计数场景换 **`LongAdder`** —— 它把热点**分散到多个格子**再汇总，吞吐高得多；
4. **原子性 ≠ 线程安全**：原子类只保护**单个操作**，像「先 `get` 判断再 `set`」这种**复合逻辑**，仍然要用 `compareAndSet` 循环或加锁。

> 一句话记：**原子类 = 用 CAS 把「读-改-写」变成一个动作；单变量用它、多变量打包用 `AtomicReference`、ABA 用带版本号的、高并发计数用 `LongAdder`。**

#### 2.2.10 常见锁的分类与使用场景

**一句话理解**：锁不是「一种」，而是**多个维度上的分类**；同一把锁可以同时属于多个类别，比如 `new ReentrantLock(true)` = 悲观 + 独占 + 可重入 + 公平 + 阻塞 + API 层锁。

**维度一：按对冲突的态度分**（详见 2.2.7、2.2.8）

| 类型 | 定义 | 代表 | 适用场景 |
|---|---|---|---|
| 悲观锁 | 认为一定会冲突，每次操作先加锁，临界区同时只允许一个线程 | `synchronized`、`ReentrantLock`、`select ... for update` | 写多、竞争激烈 |
| 乐观锁 | 认为不会冲突，先执行，冲突了用 CAS 重试 | CAS、`AtomicInteger`、数据库版本号 | 读多写少、冲突少 |

**维度二：按 JVM 内部状态分**（只针对 `synchronized`，详见 2.2.6）

| 类型 | 定义 | 适用场景 |
|---|---|---|
| 无锁 | 不加锁，CAS 重试 | 无竞争 |
| 偏向锁 | Mark Word 记线程 ID，同一线程再进直接放行 | 只有一个线程访问同步块 |
| 轻量级锁 | 栈帧锁记录 + CAS + 自旋抢锁 | 追求响应时间，同步块执行很快 |
| 重量级锁 | 竞争失败真正阻塞，进 monitor 等待队列 | 追求吞吐量，同步块执行时间长 |

**维度三：按同一线程能否重复获取分**

| 类型 | 定义 | 代表 | 适用场景 |
|---|---|---|---|
| 可重入锁 | 同一线程已持有锁时，可再次获取（底层是 state 计数 +1） | `synchronized`、`ReentrantLock`、`ReentrantReadWriteLock` | 绝大多数场景，递归调用、方法嵌套加锁 |
| 不可重入锁 | 同一线程再取也会死锁 / 失败 | `StampedLock` | 明确不会嵌套、想要更高性能 |

> 可重入的代价：`lock()` 几次就要 `unlock()` 几次，否则锁不释放。`ReentrantLock` 的 state 就是重入次数。

**维度四：按是否按先来后到排队分**

| 类型 | 定义 | 代表 | 适用场景 |
|---|---|---|---|
| 公平锁 | 按等待队列的先后顺序拿锁，不插队 | `new ReentrantLock(true)` | 要避免饥饿、要求顺序可控 |
| 非公平锁 | 直接抢，抢到就是我的，抢不到才排队 | `new ReentrantLock()`（默认）、`synchronized` | 默认选择，吞吐量更高 |

> 非公平不等于一定乱序：队首线程被唤醒后仍会先尝试抢一次，只是新来的线程有机会插队。

**维度五：按同一时刻多少人能持有分**

| 类型 | 定义 | 代表 | 适用场景 |
|---|---|---|---|
| 独占锁（互斥锁 / 排他锁） | 同一时刻只有一个线程能持有 | `synchronized`、`ReentrantLock` | 写操作、临界区修改 |
| 共享锁 | 同一时刻允许多个线程同时持有 | `Semaphore`、`CountDownLatch`（AQS 共享模式） | 限流、并发配额 |
| 读写锁 | 读共享 + 写独占，读读并发、读写 / 写写互斥 | `ReentrantReadWriteLock`、`StampedLock` | 读多写少（缓存、配置） |

读写锁两个关键点：

- **支持锁降级**：持有写锁时可以再获取读锁（写完立刻读，保证中间不被别人插进来改）。
- **不支持锁升级**：持有读锁时不能再获取写锁（会死锁）。

**维度六：按拿不到锁怎么办分**

| 类型 | 定义 | 适用场景 |
|---|---|---|
| 阻塞锁 | 拿不到就挂起线程，让出 CPU | 临界区执行时间长 |
| 自旋锁 | 拿不到就忙等循环重试，不挂起 | 临界区极短，挂起 / 唤醒的开销比等待还贵 |
| 自适应自旋 | 由 JVM 根据历史成功率决定自旋次数 | JDK 6+ 轻量级锁的默认行为 |

> 自己实现自旋锁就是 `while (!cas(...)) {}`，但长时间自旋会白烧 CPU。

> 记忆：锁的分类是**多个维度**，别把它们当成互斥的选项；选锁先问三件事 —— 读多还是写多？要不要超时 / 中断 / 公平？临界区执行时间长还是短？

#### 2.2.11 等待 / 通知：Object 与 Condition 的主要方法

**一句话理解**：每个对象都可以调用 `Object` 的 `wait/notify` 实现等待 / 通知机制，而 `Condition` 接口提供了**类似的一套方法**，只是宿主从「对象的 monitor」换成了「`Lock`」。

**① `Object` 的等待 / 通知方法**

前提：必须写在 `synchronized` 块里（锁是该对象的 monitor），否则抛 `IllegalMonitorStateException`。

| 方法 | 说明 |
|---|---|
| `wait()` | 无限期等待，直到被 `notify()` / `notifyAll()` 唤醒（等价于 `wait(0)`） |
| `wait(long timeout)` | 超时等待，最多等 `timeout` 毫秒 |
| `wait(long timeout, int nanos)` | 更精确的超时等待（毫秒 + 纳秒） |
| `notify()` | 唤醒**该对象等待队列中**的一个线程（不保证是哪一个） |
| `notifyAll()` | 唤醒**该对象等待队列中**的全部线程 |

用法：

```java
synchronized (lock) {
    while (!condition) {      // 必须 while，防虚假唤醒
        lock.wait();
    }
    // 干活
    lock.notifyAll();
}
```

**② `Condition` 的等待 / 通知方法**

前提：必须先持有 `Lock`，通过 `lock.newCondition()` 拿到 Condition，否则同样抛 `IllegalMonitorStateException`。

| 方法 | 说明 |
|---|---|
| `await()` | 无限期等待，直到被 `signal()` / `signalAll()` 唤醒（对应 `wait()`） |
| `awaitUninterruptibly()` | 等待且**不响应中断**（`Object` 没有对应方法） |
| `awaitNanos(long nanosTimeout)` | 超时等待，**返回剩余纳秒数**（返回值 ≤ 0 表示是超时返回） |
| `await(long time, TimeUnit unit)` | 超时等待，单位可选，**返回 boolean**（`true` = 超时前被唤醒，`false` = 时间到） |
| `awaitUntil(Date deadline)` | 等到**指定的绝对时间点**，同样返回 boolean |
| `signal()` | 唤醒**该 Condition 等待队列中**的一个线程（对应 `notify()`） |
| `signalAll()` | 唤醒**该 Condition 等待队列中**的全部线程（对应 `notifyAll()`） |

用法：

```java
lock.lock();
try {
    while (!condition) {      // 同样必须 while
        cond.await();
    }
    // 干活
    cond.signalAll();
} finally {
    lock.unlock();            // Lock 不会自动释放，必须 finally
}
```

**③ 两套方法的对应关系**

- `wait()` ↔ `await()`
- `wait(long)` ↔ `await(long, TimeUnit)` / `awaitNanos(long)` / `awaitUntil(Date)`
- `notify()` ↔ `signal()`
- `notifyAll()` ↔ `signalAll()`
- `Condition` 比 `Object` 多出来的能力：**不可中断等待**（`awaitUninterruptibly`）、**能知道超时后的剩余时间**、**等到某个绝对时间点**。

**④ 为什么会有两套**

- `Object` 的方法绑在 `synchronized` 的对象 monitor 上，一个对象**只有一条等待队列**，所以只能「唤醒一个（不知道是谁）」或「全部唤醒」，容易惊群。
- `Condition` 绑在 `Lock` 上，**一个锁可以 `new` 出多个 Condition**，于是能做到「只唤醒该醒的那批线程」。比如 `ArrayBlockingQueue` 就用了 `notEmpty` 和 `notFull` 两个 Condition：生产者只唤醒消费者，消费者只唤醒生产者。

> 记忆：名称不同、宿主不同、能力不同 —— `Object` 的 `wait/notify` 绑在对象 monitor 上（一个对象一条队列），`Condition` 的 `await/signal` 绑在 `Lock` 上（一个锁可以有多条队列），且额外支持不可中断等待和更精细的超时控制。

#### 2.2.12 LockSupport：阻塞和唤醒线程

**一句话理解**：`LockSupport` 是 AQS 的「底座」，用来**阻塞和唤醒线程**，但它不用锁、也不抛中断异常 —— 靠的是一个**许可（permit）**，就像一张最多只能用一次的通行证。

**① 阻塞当前线程的方法**

全部是 `static` 方法，返回 `void`，阻塞的都是**当前线程**。

| 方法 | 说明 |
|---|---|
| `park()` | 阻塞当前线程，除非许可可用；可能**虚假唤醒**（无理由返回） |
| `park(Object blocker)` | 同上，并记录 `blocker`，**便于 jstack 排查**（能看到卡在哪个对象上） |
| `parkNanos(long nanos)` | 最多阻塞 `nanos` 纳秒 |
| `parkNanos(Object blocker, long nanos)` | 带 blocker 的超时阻塞 |
| `parkUntil(long deadline)` | 阻塞到**绝对时间** `deadline`（毫秒时间戳，从 1970 起算） |
| `parkUntil(Object blocker, long deadline)` | 带 blocker 的绝对时间阻塞 |

**② 唤醒线程的方法**

| 方法 | 说明 |
|---|---|
| `unpark(Thread thread)` | 给**指定线程**发放一个许可，让它从 `park` 返回（可以精确指定是哪个线程） |
| `getBlocker(Thread t)` | 获取线程当前尚未解除的 `blocker`（排查用） |

**③ 最基础的一对用法**

```java
Thread t = new Thread(() -> {
    System.out.println("子线程准备 park");
    LockSupport.park();                 // 等许可
    System.out.println("子线程被唤醒");
});
t.start();

Thread.sleep(1000);
LockSupport.unpark(t);                  // 唤醒指定线程
```

**④ 许可机制的关键特性**

- **许可最多只有 1 个，且不可累积**：连续 `unpark` 两次，之后也只有**第一次** `park` 会立即返回。
- **`unpark` 可以先于 `park` 执行**：许可会「存」下来，等线程 `park` 时直接消费掉、立即返回，不会阻塞。

```java
LockSupport.unpark(Thread.currentThread());   // 先发许可
LockSupport.park();                           // 立即返回，不阻塞
LockSupport.park();                           // 许可已用完 → 这次才真阻塞
```

对比 `wait/notify`：**先 `notify` 后 `wait` 是无效的（通知会丢失），而先 `unpark` 后 `park` 是有效的。**

**⑤ 两个必须注意的行为**

**① 被中断时不抛异常**，只是让 `park` 立即返回，而且**中断标志会保留**：

```java
LockSupport.park();                 // 线程被 interrupt() → 这里立即返回
if (Thread.interrupted()) {         // 需要自己检查（并清除标志）
    // 处理中断
}
```

**② 可能虚假唤醒** → 必须写在 `while` 循环里：

```java
while (!canProceed()) {             // 和大一统的 wait 一样，用 while 而不是 if
    LockSupport.park(this);         // 传 blocker，jstack 里能看到
}
```

**⑥ 和 `wait/notify` 的区别**

| 对比项 | `wait` / `notify` | `park` / `unpark` |
|---|---|---|
| 是否要求持有锁 | 必须在 `synchronized` 块内 | **不要求持锁** |
| 是否释放锁 | `wait` 会释放 monitor | **不释放任何锁** |
| 先唤醒后等待 | 无效，通知会丢失 | **有效**，许可会留存（最多 1 个） |
| 能否指定唤醒哪个线程 | 不能（队列里挑一个，不确定是谁） | **能**，`unpark(Thread)` 精确指定 |
| 中断响应 | 抛 `InterruptedException` | **不抛异常**，仅立即返回（标志保留） |
| 超时后的信息 | 无法区分超时还是被唤醒 | 无法区分（返回 `void`，需自己用 `System.nanoTime()` 判断） |
| 虚假唤醒 | 有，必须 `while` | 有，必须 `while` |

**⑦ 它和 AQS 的关系**

AQS 自己不做阻塞，**靠的就是 `LockSupport`**：

```java
// AQS 内部（简化）
LockSupport.park(this);                 // blocker 传的是 AQS 实例本身
LockSupport.unpark(s.thread);           // 在 unparkSuccessor 里唤醒后继节点
```

这也解释了 `jstack` 里 `ReentrantLock` 卡住时显示的是：

```
- parking to wait for <0x000000076ab0e0d0> (a java.util.concurrent.locks.ReentrantLock$NonfairSync)
```

后面括号里的对象，正是 `park` 时传进去的那个 `blocker`。

**⑧ 四个常见的坑**

1. **许可只有 1 个** → 多次 `unpark` 不会"攒次数"，第二次 `park` 照样阻塞。
2. **`park` 不释放锁** → 这点和 `wait` 完全相反，持锁时 `park` 会导致别人永远拿不到锁。
3. **不抛 `InterruptedException`** → 中断只是让 `park` 立即返回，容易漏掉中断处理。
4. **别用 `if` 包 `park`** → 虚假唤醒 + 条件被改回去，都会让逻辑出错。

> 记忆：`LockSupport` = 「许可版」的阻塞唤醒 —— **一个许可、不可累积、可提前发放、能指名唤醒、不抛中断异常、不要求也不释放锁**；AQS 的阻塞唤醒全靠它。

#### 2.2.13 AQS（AbstractQueuedSynchronizer）

**一句话理解**：AQS 是 `java.util.concurrent.locks` 包里的一个抽象基类（JDK 5 由 Doug Lea 引入），它把「同步状态」和「线程排队阻塞」抽象出来 —— **子类只需要定义「state 等于几算拿到锁」，排队、阻塞、唤醒、中断处理它全包了**。`ReentrantLock`、`Semaphore`、`CountDownLatch` 都建在它上面。

**三大核心部件**

| 部件 | 内容 | 作用 |
|---|---|---|
| ① 同步状态 `state` | `volatile int state` + `compareAndSetState()` | 「锁」的实质：CAS 把 state 从 0 改成 1 就算拿到了 |
| ② FIFO 双向等待队列 | `Node`（`prev` / `next` / `thread` / `waitStatus`） | 抢不到的线程在这里**排队** |
| ③ 模板方法 | 子类重写 `tryAcquire` / `tryRelease`（独占）、`tryAcquireShared` / `tryReleaseShared`（共享）；父类的 `acquire` / `release` 是 `final` 的 | 只把「怎么判断能不能拿锁」留给子类 |

**同样是 AQS 的子类，`state` 的含义完全由子类自定义**：

| 实现类 | `state` 表示什么 |
|---|---|
| `ReentrantLock` | 锁的**重入次数**（0 = 未锁） |
| `Semaphore` | 剩余的**许可数** |
| `CountDownLatch` | 还差几个计数没归零 |
| `ReentrantReadWriteLock` | 高 16 位 = 读锁次数，低 16 位 = 写锁次数 |

**① 加锁流程（以 `ReentrantLock` 为例）**

```text
lock()
  │
  ├─ ① CAS 把 state 从 0 改成 1
  │     成功 → 记下「锁归我」(exclusiveOwnerThread = 当前线程)，结束 ✅
  │
  ├─ ② 失败，但发现锁的持有者就是我自己
  │     → state++（重入计数 +1），结束 ✅
  │
  └─ ③ 还是不行（别人占着）
        → 把自己包装成 Node，挂到队尾
        → 自旋看一眼：我是队头吗？是就再抢一次
        → 抢不到 → LockSupport.park() 睡过去
        → 被唤醒后回到「抢一次」，抢到就出队 ✅
```

**② 解锁流程**

```text
unlock()
  │
  ├─ ① state - 1
  │     结果 > 0 → 只是重入少了一层，直接返回（还没真释放）
  │
  ├─ ② 结果 == 0 → 真正释放
  │     清空 exclusiveOwnerThread，state 归 0
  │
  └─ ③ 唤醒队列里第一个「有效」节点
        → 那个线程从 park 醒来，回到加锁流程去抢锁
```

**③ 一个具体时序**

```text
A: lock()   → CAS 成功                → 持锁
B: lock()   → CAS 失败 → 入队 → park
C: lock()   → CAS 失败 → 入队 → park
A: unlock() → state = 0 → 唤醒 B
B: 醒来抢锁 → 成功                     → 持锁
B: unlock() → state = 0 → 唤醒 C
C: 醒来抢锁 → 成功                     → 持锁
```

**④ 三个配套细节**

| 细节 | 说明 |
|---|---|
| 为什么先自旋再 `park` | 直接挂起 / 唤醒的代价高；先短促试抢一下（可能马上就能拿到），兼顾响应速度和 CPU |
| 公平 vs 非公平的差别 | 只在「抢一次」那一步：非公平直接抢（新来的线程也能插队）；公平先看 `hasQueuedPredecessors()`，有人排队就乖乖去排队 |
| 共享模式 | 换个判断而已：`state` 表示「剩余许可」（如 `Semaphore`、`CountDownLatch`），`tryAcquireShared` 返回 ≥ 0 就允许**多个线程同时通过** |

**⑤ 和前面几节的关系**

- **重入**（见 2.2.5）：就是 AQS 的 `state++ / state--`；
- **阻塞 / 唤醒**（见 2.2.12）：AQS 自己不阻塞，靠 `LockSupport.park / unpark`；
- **`jstack` 里的 `parking to wait for ... (ReentrantLock$NonfairSync)`**：括号里那个对象，正是 `park(blocker)` 传进去的 AQS 实例；
- **和 `synchronized` 是两个体系**：`synchronized` 由 JVM 实现（monitor，锁信息挂在对象头上）；AQS 是用 Java 写的，跑在 **CAS + `volatile` + `park`** 这三个 JVM 原语之上。
- **源码入口**：`ReentrantLock.lock()` → `Sync.acquire(1)` → AQS 的 `acquire()`，正好是上面那四步：`tryAcquire` → `addWaiter` → `acquireQueued` → `selfInterrupt`。

> 记忆：**AQS = 一个 `volatile int` 状态 + 一条 FIFO 双向队列 + 一套模板方法。加锁 = CAS 抢 state，抢不到就入队 + `park`；解锁 = state 归零后 `unpark` 队头。**

#### 2.2.14 通信工具类（`CountDownLatch` / `CyclicBarrier` / `Semaphore` / `Exchanger` / `Phaser`）

**一句话理解**：JUC 里让**多个线程互相协调、等待、限流**的一组工具，**都基于 AQS 的共享模式**实现（见 2.2.13）—— AQS 负责排队 / 阻塞 / 唤醒，工具类负责定义「`state` 等于几算通过」。

**① 先建立直觉**

| 工具 | 一句话 | 生活类比 |
|---|---|---|
| `CountDownLatch` | **一个（或几个）线程等一批线程干完**，倒计时归零放行 | 发令枪，**只响一次** |
| `CyclicBarrier` | **多个线程互相等**，到齐一起走，**可反复用** | 旅行团集合点，人齐才发车 |
| `Semaphore` | **限制同时干活的线程数**（发许可证） | 停车场车位 / 限流闸机 |
| `Exchanger` | **两个线程交换数据**，配对后各拿对方的 | 一手交钱一手交货 |
| `Phaser` | **多阶段、可动态增减参与者**的同步 | 多轮淘汰赛关卡 |

**② 逐个细讲**

**`CountDownLatch`（倒计时门闩）**

- 初始化计数 `new CountDownLatch(n)`；别的线程 `countDown()`（计数 -1）；等待方 `await()` **阻塞到计数归零**。
- **角色不对称**：`await` 的线程在等，`countDown` 的线程干完就走、**不等待**。
- 场景：主线程等 N 个子任务全部完成再汇总；并发压测「等所有线程就绪后同时发压」。

**`CyclicBarrier`（循环栅栏）**

- 初始化 `new CyclicBarrier(parties)`；每个线程到点 `await()`，**凑齐 `parties` 个后一起继续**，栅栏**自动重置可复用**。
- 可传一个 `Runnable`（**barrierAction**）：每凑齐一批**先执行它**再放行（适合「每轮结束汇总」）。
- **角色对称**：参与的线程**都 `await`、都等待**。

**`Semaphore`（信号量 / 许可证）**

- 初始化 `new Semaphore(permits)`；`acquire()` 拿许可（拿不到就等），`release()` 还许可。
- 用途：限制**同时并发**数量（如最多 10 个线程访问数据库），或当**互斥锁**用（`permits = 1`）。
- **和锁的区别**：锁要求「谁加锁谁解锁」；信号量**不要求**，可以在 A 线程 `acquire`、B 线程 `release`。

**`Exchanger`（交换器）**

- `exchange(v)`：**两个线程**互相等，都到了就把各自的数据**交换**给对方；只适合**两方配对**，业务用得少。

**`Phaser`（相位器）**

- `CyclicBarrier` 的**通用增强版**：支持**多阶段**、参与者可**动态注册 / 注销**；实际项目极少手写，**了解即可**。

**③ 选型速查**

| 需求 | 选谁 |
|---|---|
| 等一批任务全部完成（**一次性**） | `CountDownLatch` |
| 多线程互相等齐、**要复用 / 多轮** | `CyclicBarrier` |
| 限制**并发数量** / 资源池 | `Semaphore` |
| **两个**线程交换数据 | `Exchanger` |
| **多阶段 + 动态数量** | `Phaser` |

> **必考：`CountDownLatch` vs `CyclicBarrier`** —— 前者**一次性、递减计数、角色不对称**（等的和干活的不是同一批）；后者**可循环、到齐即走、角色对称**（参与线程都等）。

**④ 坑**

1. **`CountDownLatch` 不能重置**，用完即废；要复用就 `CyclicBarrier` 或 new 一个；
2. **`countDown()` 必须放 `finally`**：否则某个线程异常没 `countDown`，等待方会**永久 `await` 卡死**；
3. **`await()` / `acquire()` 尽量带超时**；`CyclicBarrier` 的线程被中断会抛 `BrokenBarrierException`（栅栏「破了」）；
4. **`Semaphore` 的 `release()` 必须和 `acquire()` 配对**：不 `acquire` 就 `release` 会让**许可数凭空变多**，是经典误用。

> 一句话记：**`CountDownLatch` 一次等一批、`CyclicBarrier` 循环等齐、`Semaphore` 控并发数、`Exchanger` 两方换数据、`Phaser` 多阶段 —— 全建在 AQS 共享模式上。**

#### 2.2.15 ReentrantLock：显式锁

**一句话理解**：`ReentrantLock` 是 JUC 里基于 **AQS**（见 2.2.13）实现的**可重入互斥锁**，相当于 `synchronized` 的「显式版」—— 需要自己 `lock()` / `unlock()`，换来的是更多能力（可中断 / 超时 / 公平 / 多条件队列）。

**① 定位**

- **互斥**：同一时刻只允许一个线程持锁；
- **可重入**：同一个线程可以重复加锁，靠 AQS 的 `state` 计数（加一次 +1、解一次 −1，减到 0 才真正释放）；
- **两套体系**：`synchronized` 由 JVM 实现（monitor，锁信息挂在对象头上）；`ReentrantLock` 用 Java 写，跑在 **CAS + `volatile` + `park`** 之上。

**② 用法模板（`unlock` 必须放 `finally`）**

```java
ReentrantLock lock = new ReentrantLock();
lock.lock();
try {
    // 临界区：同一时刻只有一个线程能进来
} finally {
    lock.unlock();   // 必须写在 finally，否则中途抛异常就永远不解锁
}
```

**③ 何时用：它比 `synchronized` 多出的能力**

| 需求 | `synchronized` | `ReentrantLock` |
|---|---|---|
| 简单互斥 | ✅ 首选 | ✅ 但更啰嗦 |
| 等待时可被打断 | ❌ | ✅ `lockInterruptibly()` |
| 最多等多久（超时） | ❌ | ✅ `tryLock(超时)` |
| 公平（先来先得） | ❌ | ✅ 构造时 `new ReentrantLock(true)` |
| 多个等待队列 | ❌（一个对象一条） | ✅ `newCondition()` 可建多条 |

> 选型结论：**没特殊需求就用 `synchronized`；需要「中断 / 超时 / 公平 / 多条件队列」才上 `ReentrantLock`。**

**④ 坑**

- **`unlock()` 必须放 `finally`**：中途抛异常就永远不解锁 → 别的线程一直卡住（`synchronized` 不会有这问题）；
- **加了才解、次数对等**：没加锁就 `unlock()` 会抛 `IllegalMonitorStateException`；重入几次就要解几次；
- 默认**非公平**（吞吐高），公平模式性能下降；
- 别因为它「更灵活」就无脑替换 `synchronized` —— 手动释放，用错代价更大。

> 记忆：**ReentrantLock = 手动加解锁的可重入互斥锁，基于 AQS；比 `synchronized` 多了「中断 / 超时 / 公平 / 多条件队列」；最大的坑是 `unlock()` 一定要放 `finally`。**

#### 2.2.16 ThreadLocal：线程的「本地变量」

**一句话理解**：`ThreadLocal` 是线程的「本地变量」—— 每个线程都持有该变量的**一份副本**，人手一份、各用各的，从根上避免了共享资源的竞争。

**① 思想：换个思路绕开共享**

线程安全问题的核心，在于多个线程会访问同一个临界区的**共享资源**。那如果每个线程都拥有自己的「共享资源」，各用各的、互不影响，就不会出现线程安全问题了。

这其实是一种**「空间换时间」**的思想：每个线程各持一份副本，虽然**内存占用变大了**，但由于**不需要同步**，也就减少了线程可能存在的阻塞问题，从而提高了时间上的效率。

**② 核心用法（每个线程一份副本）**

```java
ThreadLocal<Integer> local = ThreadLocal.withInitial(() -> 0);

local.set(1);            // 存进「当前线程」自己的那份副本
Integer v = local.get(); // 只读当前线程的那份，别的线程改了也不影响
local.remove();          // 用完必须清理
```

**常用方法（常用的就这 5 个）**

| 方法 | 作用 | 备注 |
|---|---|---|
| `T get()` | 读**当前线程**那份副本 | 没有值时：若重写了 `initialValue()` 就用它初始化，否则返回 `null` |
| `void set(T value)` | 写**当前线程**那份副本 | 只影响当前线程 |
| `void remove()` | 删除**当前线程**那份副本 | **防内存泄漏的关键**，用完必调 |
| `protected T initialValue()` | 提供初始值 | 默认返回 `null`，需继承重写（现在更常用下面的 `withInitial`） |
| `static ThreadLocal<S> withInitial(Supplier<? extends S>)` | 工厂方法，一步指定初始值 | Java 8+，最常用的创建方式 |

> 一句话记：**`get` 读、`set` 写、`remove` 清（必调）、`withInitial` 建（带初始值）。**

**③ 原理（为什么是「人手一份」）**

- 数据其实存在**每个线程自己的 `ThreadLocalMap` 里**（它是 `Thread` 对象的一个字段），**不是存在 `ThreadLocal` 对象里**；
- `ThreadLocal` 只是个 **key**：`get()` 就是「拿当前线程 → 找到它的 map → 用 `this` 当 key 取值」；
- 所以线程之间天然隔离，各取各的，互不干扰。

**④ 坑**

- **内存泄漏**：`ThreadLocalMap` 的 **key 是弱引用**（WeakReference，GC 时可能被回收），**value 是强引用**；key 被回收后 value 还在，线程池里线程长期存活 → value 一直不释放。**用完必须 `remove()`**；
- **线程池污染**：线程被复用，上一个任务留下的值可能被下一个任务读到 → 所以 `remove()` 要写在 `try/finally` 里；
- **不解决原子性**：它只是「各用各的」；如果多个线程**共用**同一个 `ThreadLocal` 变量并做复合操作，照样有并发问题。

**⑤ 和锁的区别（两种思路）**

| | 锁（`synchronized` / `Lock`） | `ThreadLocal` |
|---|---|---|
| 思路 | 共享一份，排队访问 | 各存一份，互不干扰 |
| 代价 | 阻塞、线程上下文切换 | 内存占用变大 |
| 适用 | 数据必须所有线程共享同一份 | 数据本就该按线程隔离 |



> 记忆：**ThreadLocal = 每个线程一份副本的「本地变量」，用「空间换时间」绕开共享、从根上避免竞争；数据实际存在线程自己的 `ThreadLocalMap` 里，用完必须 `remove()`，否则线程池里会内存泄漏。**

### 2.3 并发容器（队列与 CopyOnWrite）

并发容器存在的意义，是让我们**不用自己写加锁逻辑**就能在多线程下安全地读写集合。这一节重点讲两类：负责「传数据 / 排队」的**阻塞队列**，和负责「读多写少」的**写时复制容器**。

#### 2.3.1 生产者-消费者模式与阻塞队列（BlockingQueue）

**一句话理解**：资源存储在一个**缓冲池**中，生产者把生产的资源存进缓冲池，消费者从缓冲池里取走资源消费，这就是**生产者-消费者模式**；而 `BlockingQueue` 就是 JDK 帮我们把「池空阻塞消费者、池满阻塞生产者」这套等待-唤醒逻辑封装好的**线程安全缓冲池**。

**① 先看问题：自己写等待-唤醒有多容易出错**

缓冲池本质是一个被多个生产者、消费者并发读写的共享容器。如果自己用 `wait/notify` 实现：

- 缓冲池**空了** → 需要阻塞消费者、唤醒生产者；
- 缓冲池**满了** → 需要阻塞生产者、唤醒消费者；
- 还要处理虚假唤醒（`while` 循环判断）、只唤醒对的一方、加锁范围……

这套「等待-唤醒逻辑」写起来又长又容易出错，JDK 当然帮我们做了 —— 这就是 `BlockingQueue`。你只管往里存、取就行，而不用担心多线程环境下存、取共享变量的线程安全问题。

**② BlockingQueue 是什么**

- `java.util.concurrent.BlockingQueue` 是一个**接口**，继承了 `Queue`。
- **生产者**：往队列里添加元素的线程；**消费者**：从队列里拿元素的线程。
- `BlockingQueue` 就是存放元素的容器，方法分为**插入、移除、检查**三类。
- 注意：`BlockingQueue` **不允许存 `null`**（会抛 `NullPointerException`）。

**③ 核心方法：三组操作 × 四种处理方式**

每个操作在「队列满 / 队列空」时有四种应对策略：

| 操作 | 抛异常 | 返回特殊值 | 阻塞 | 超时 |
|---|---|---|---|---|
| 插入 | `add(e)` | `offer(e)` | `put(e)` | `offer(e, time, unit)` |
| 移除 | `remove()` | `poll()` | `take()` | `poll(time, unit)` |
| 检查 | `element()` | `peek()` | — | — |

- **抛异常**：`add` 队满抛 `IllegalStateException`；`remove` / `element` 队空抛 `NoSuchElementException`。
- **返回特殊值**：`offer` 队满返回 `false`；`poll` / `peek` 队空返回 `null`。
- **阻塞**：`put` 队满就等、`take` 队空就等，直到能存 / 能取。
- **超时**：`offer` / `poll` 带上时间参数，到时还不行就返回 `false` / `null`。

> 实际开发用 `put` / `take`（阻塞）和 `offer` / `poll`（带超时）最多；`add` / `remove` 因为抛异常，一般只在确定不会满 / 空时用。

**④ 常见阻塞队列速查（定位 / 选型 / 坑）**

> 先统一两个词：**有界** = 容量有上限，满了 `put` 会阻塞，能形成**背压**（用等待拖住生产方，防止压垮系统）；**无界** = 理论上不限容量，生产快于消费会一直堆积，最终可能内存耗尽报 `OutOfMemoryError`（简称 **OOM**）。

**`ArrayBlockingQueue` —— 定长数组缓冲池**

- **定位**：数组实现的**有界**阻塞队列，创建时**必须指定容量**；内部一把 `ReentrantLock` + `notEmpty` / `notFull` 两个 `Condition`。
- **选型**：容量固定、想要**内存可预测**的缓冲池；需要背压的生产者-消费者。
- **坑**：容量**不能扩容**，太小频繁阻塞、太大白占内存；默认**非公平**（新线程可插队抢锁，吞吐高，传 `fair=true` 变公平＝严格先到先得但更慢）；只有**一把锁**，入队和出队会互相竞争。

**`LinkedBlockingQueue` —— 并发度更高的链表队列**

- **定位**：链表实现的**可选有界**阻塞队列，入队、出队**各用一把锁**（两把锁），并发度高于 `ArrayBlockingQueue`；不指定容量默认 `Integer.MAX_VALUE`，**近似无界**。
- **选型**：线程池的默认任务队列（`newFixedThreadPool` / `newSingleThreadExecutor` 都用它）；要高并发入队出队的生产者-消费者。
- **坑**：**不指定容量 ≈ 无界** → 堆积到最后 **OOM**，这正是 `Executors` 建线程池的经典坑；每次入队要 `new` 一个节点对象，**垃圾回收（GC）**压力比数组大；两把锁提升了并发，但 `size()` 仍是**近似值**。

**`PriorityBlockingQueue` —— 带优先级的队列**

- **定位**：**无界**阻塞队列，元素按**优先级**出队（实现 `Comparable` 或用 `Comparator` 指定比较规则），不是先进先出。
- **选型**：任务要按紧急程度处理（按优先级调度的任务队列）。
- **坑**：**只保证出队有序**，`iterator()` 遍历顺序不保证；**无界** → 有堆积 OOM 风险；元素**必须可比较**，否则抛 `ClassCastException`，且**同优先级元素顺序不保证**。

**`SynchronousQueue` —— 容量为 0 的「一手交钱一手交货」**

- **定位**：**不存储元素**（容量为 0）：`put` 必须等到有 `take` 配对才返回，反之亦然，像**直接把东西递到对方手上**。
- **选型**：需要「直接传递、不缓冲」的场景；`newCachedThreadPool` 用的就是它（任务直接交给空闲线程，没有空闲就新建线程）。
- **坑**：**没有容量**，没有配对方就一直阻塞，**当普通缓冲队列用会卡死**；公平模式（严格配对排队）性能更差，默认非公平。

**`DelayQueue` —— 延迟到期才能取**

- **定位**：**无界**阻塞队列，元素必须实现 `Delayed` 接口，**只有延迟到期**的元素才能被取出，队头是**最先到期**的那个（底层按到期时间排序，不是先进先出）。
- **选型**：延迟 / 定时任务，如**订单超时关闭**、缓存过期、失败重试。
- **坑**：`take()` 会**阻塞到有元素到期**才返回；元素**必须实现 `Delayed`**；**无界** → 有堆积 OOM 风险。

**`LinkedTransferQueue` —— 能把元素「直接递交」给消费者**

- **定位**：链表实现的**无界**阻塞队列，相当于 `ConcurrentLinkedQueue` + `BlockingQueue` 的结合；独有 `transfer(e)`：生产者**直接把元素交给正在等待的消费者**，不放进队列（没人等就阻塞）。
- **选型**：需要「生产者直接交付」的高性能传递场景（如消息中转）。
- **坑**：只有 `transfer` 是它的特色，当普通阻塞队列用没发挥价值；**无界** → 有堆积 OOM 风险。

**`LinkedBlockingDeque` —— 双端队列**

- **定位**：链表实现的**可选有界双端**阻塞队列，**两头都能进出**，可用于「**工作窃取**」（空闲线程从队列另一端「偷」任务来做，见 `ForkJoinPool`）。
- **选型**：需要双端操作的场景（工作窃取、deque 语义）。
- **坑**：只用**一把锁**管两端，并发度不如 `LinkedBlockingQueue`（后者两把锁）；不指定容量 **≈ 无界**，有堆积 OOM 风险。

**选型速查**

| 需求 | 选谁 |
|---|---|
| 固定容量缓冲池、内存可控 | `ArrayBlockingQueue` |
| 高并发、线程池任务队列 | `LinkedBlockingQueue` |
| 按优先级处理 | `PriorityBlockingQueue` |
| 直接传递、不缓冲 | `SynchronousQueue` |
| 延迟 / 定时到期 | `DelayQueue` |
| 生产者直接交付 | `LinkedTransferQueue` |
| 两端进出 / 工作窃取 | `LinkedBlockingDeque` |

> 线程池里的任务队列用的就是它：`newFixedThreadPool` / `newSingleThreadExecutor` 用 `LinkedBlockingQueue`，`newCachedThreadPool` 用 `SynchronousQueue`。

**⑤ 用法示例：一生产者一消费者**

```java
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ProducerConsumerDemo {
    public static void main(String[] args) {
        BlockingQueue<Integer> queue = new LinkedBlockingQueue<>(10); // 容量 10

        // 生产者
        new Thread(() -> {
            try {
                for (int i = 0; i < 100; i++) {
                    queue.put(i);              // 队列满了会自动阻塞
                    System.out.println("生产：" + i);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        // 消费者
        new Thread(() -> {
            try {
                while (true) {
                    Integer x = queue.take();  // 队列空了会自动阻塞
                    System.out.println("消费：" + x);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}
```

**⑥ 内部原理：靠的就是 `Condition`**

以 `ArrayBlockingQueue` 为例，内部是一把 `ReentrantLock` 加两个 `Condition`（见 2.2.11）：

- `notEmpty`：队列不空、消费者可以取 → **生产者放入元素后** `signal` 它；
- `notFull`：队列不满、生产者可以放 → **消费者取出元素后** `signal` 它。

于是能做到**生产者只唤醒消费者、消费者只唤醒生产者**，这正是 `BlockingQueue` 把等待-唤醒逻辑封装好的底层实现。

> 记忆：**`BlockingQueue` = 线程安全的缓冲池：队满 `put` 阻塞、队空 `take` 阻塞；三组操作（插入 / 移除 / 检查）× 四种策略（抛异常 / 特殊值 / 阻塞 / 超时）；底层就是 `Lock` + `notEmpty` / `notFull` 两个 `Condition`。**

#### 2.3.2 CopyOnWrite：写时复制容器

**一句话理解**：`CopyOnWrite`（写时复制）是一种**并发场景下常用的设计思想** —— 有调用者要修改数据源时，不直接改原数据，而是**先复制一份副本**给调用者改；容器层面就是**写的时候复制出一个新容器，改完再把引用指向新容器**，从而让「读」完全不用加锁。

**① 什么是写时复制**

> 当有多个调用者同时请求一个资源数据时，如果有一个调用者出于某些原因需要对当前数据源进行修改，这时系统会**复制一个当前数据源的副本**给这个调用者去改，而不是改动原数据源。

**② 容器里的具体做法**

`CopyOnWriteArrayList` 这类**写时复制容器**，往容器里添加元素时不是直接加，而是：

1. 先加锁（**只锁写操作**）；
2. 将当前容器 **copy** 一份，复制出一个**新的容器**；
3. 往**新容器**里添加我们需要的元素；
4. 最后把原容器的**引用指向新容器**。

```java
// CopyOnWriteArrayList 的核心逻辑（简化）
public boolean add(E e) {
    synchronized (lock) {                                // 写操作加锁
        Object[] es = getArray();
        Object[] newArray = Arrays.copyOf(es, es.length + 1); // ① 复制出新数组
        newArray[es.length] = e;                         // ② 往新数组里加
        setArray(newArray);                              // ③ 引用指向新数组（volatile 写）
        return true;
    }
}

public E get(int index) {
    return (E) getArray()[index];                        // 读操作：直接读，不加锁
}
```

**③ 好处：读写分离，读不加锁**

- 读操作读的是**当前那个数组引用**，**不需要加锁**，性能高、也不会被写阻塞；
- 写操作在副本上做，**不影响正在读的线程**（它们读的是旧数组）；
- 迭代器拿到的是**快照**，遍历过程中容器被修改也**不会抛 `ConcurrentModificationException`**。

**④ 代价（很重要）**

| 代价 | 说明 |
|---|---|
| 内存开销大 | 每次写都要复制整个容器，写频繁时内存翻倍、GC 压力大 |
| 写性能差 | 复制 + 加锁，写得越多越慢 |
| 数据弱一致 | 读到的可能是**旧数据**（引用还没切换），只保证**最终一致** |

**⑤ 适用场景**

- **读多写少**（甚至几乎不写）的场景，比如：**监听器列表、白名单 / 黑名单、配置快照**。
- 反面：写多读少就不要用它，改用 `Collections.synchronizedList` 或 `ConcurrentHashMap` 之类。

**⑥ 常见实现**

- `CopyOnWriteArrayList`：对应 `ArrayList` 的线程安全版本；
- `CopyOnWriteArraySet`：底层就是 `CopyOnWriteArrayList` 加去重，对应 `HashSet` 的线程安全版本。

> 记忆：**CopyOnWrite = 写时复制：读不加锁（读写分离），写时先复制再改、最后换引用；代价是内存和写性能，适合读多写少（监听器、白名单）。**

#### 2.3.3 非阻塞并发队列（ConcurrentLinkedQueue / ConcurrentLinkedDeque）

**一句话理解**：`ConcurrentLinkedQueue` 是用 **CAS**（一种无锁的原子更新方式，即「比较并交换」）实现的**线程安全队列**，**无锁、无界、不阻塞** —— 高并发下不用抢锁，吞吐高；代价是没有「等待」语义，且 `size()` 又慢又不准。

**① `ConcurrentLinkedQueue` —— 无锁、无界、非阻塞队列**

- **定位**：JUC 下基于**链表 + CAS 无锁**实现的线程安全队列（先进先出 FIFO）；内部用两个 `volatile` 指针 `head` / `tail`，而且**滞后更新**（不每次都移动），以减少 CAS 重试开销。
- **选型**：需要「高并发地丢任务 / 取任务、**不需要等待**」的场景；要「空了等、满了等」的阻塞语义 → 用 `BlockingQueue`。
- **坑**：
  - `size()` 要**遍历整条链表**才能算出数量，是 O(n)，而且并发下**不准** → 判空请用 `isEmpty()`；
  - **不阻塞**：队空时 `poll()` 只返回 `null`，自己写循环会**空转**（CPU 白白打满，要加等待 / 退避）；
  - **无界**（容量不限）→ 生产快于消费时元素一直堆积，最后**内存耗尽报 `OutOfMemoryError`（简称 OOM）**。

**和 `BlockingQueue` 的本质区别**：

- **阻塞队列**（`LinkedBlockingQueue` / `ArrayBlockingQueue`）：靠**锁 + `Condition`**，队空 / 队满会**阻塞**调用线程，自带**背压**（用等待拖住生产方）；
- **非阻塞队列**（`ConcurrentLinkedQueue`）：靠 **CAS 无锁**，谁都不等，吞吐高，但**不管堆积**。

**② `ConcurrentLinkedDeque` —— 双端版本**

- **定位**：`ConcurrentLinkedQueue` 的双端版本，**两头都能进出**（`addFirst` / `addLast` / `pollFirst` / `pollLast`），同样无锁、无界、非阻塞。
- **选型**：需要双端操作的并发场景（如工作窃取、双端任务队列）。
- **坑**：和 `ConcurrentLinkedQueue` 一样，`size()` 慢且不准、无界有 OOM 风险；双端操作比单端多一些 CAS 开销。

**③ 什么时候用非阻塞队列**

- **用**：任务不需要「等」，只求高吞吐地传递（如日志 / 事件队列，消费方自己轮询，或由别的机制驱动）。
- **不用**：需要自动**背压**的场景（那是 `BlockingQueue` 的活）。

> 记忆：**`ConcurrentLinkedQueue` = 无锁（CAS）、无界、非阻塞的线程安全队列；优点是高吞吐，缺点是 `size()` 慢且不准、没有等待语义（易空转）、无界会 OOM；要阻塞 / 背压就换 `BlockingQueue`。双端版是 `ConcurrentLinkedDeque`。**

### 2.4 集合源码（ConcurrentHashMap）

**一句话理解**：`ConcurrentHashMap` 是 JUC 提供的**线程安全且高性能的哈希表**，可以理解成「HashMap 的并发版」——功能上还是 Map，但**不用手动加锁**，多线程同时读写也安全。它的核心思路是 **减小锁粒度 + 读操作不加锁**。

#### 2.4.1 为什么需要它：HashMap 不安全、Hashtable 太慢

- `HashMap` **线程不安全**：并发 `put` 可能丢数据（同一个桶被覆盖）；JDK 7 扩容时还可能形成环形链表，导致 `get` 死循环。
- `Hashtable` / `Collections.synchronizedMap` 虽然安全，但**用一把大锁锁住整张表**，任何读写都要抢同一把锁，并发度极低。
- `ConcurrentHashMap` 两者兼顾：**既安全，并发性能又高**。

#### 2.4.2 演进：JDK 7 分段锁 → JDK 8 桶级锁

| | JDK 7 | JDK 8 及以后 |
|---|---|---|
| 数据结构 | **Segment 数组 + HashEntry 数组** | **Node 数组 + 链表 + 红黑树**（和 `HashMap` 一致） |
| 加锁方式 | **分段锁**：每个 `Segment` 继承 `ReentrantLock`，只锁一段 | **CAS + `synchronized` 锁单个桶**（锁链表头 / 树根） |
| 锁粒度 | 一段（默认 16 段 → 并发度 16） | 一个桶（粒度更细，并发度更高） |
| 扩容 | 每个 Segment 独立扩容 | **多线程协助扩容**（`helpTransfer`） |

- **JDK 7 分段锁**：整张表切成默认 16 个 `Segment`，写操作只锁自己要操作的那一段，不同段互不影响，理论并发度是 16 倍。
- **JDK 8 抛弃 Segment**：直接对**数组的某个槽位**加锁 —— 桶为空就用 **CAS** 无锁插入，桶非空才 `synchronized` 锁住这个桶的头节点。锁粒度从「一段」缩到「一个桶」，冲突更少。
- 红黑树优化：链表长度 ≥ 8 且数组长度 ≥ 64 时转红黑树，查询从 O(n) 降到 O(log n)。

#### 2.4.3 关键机制

**① 读操作完全不加锁**

`get()` 不加任何锁，靠节点里的 `val`、`next` 用 `volatile` 修饰来保证**可见性** —— 线程 A 写入的值，线程 B 立刻能读到。

**② 写操作看桶的状态**

以 `put` 为例：

- 桶为空 → 用 **CAS** 直接放进去（无锁，成功即返回）；
- 桶非空 → 对**头节点加 `synchronized`**，再遍历链表 / 红黑树插入或更新；
- 遇到「扩容中的桶」（头节点是 `ForwardingNode`）→ 当前线程**先帮忙扩容**（`helpTransfer`），再回头插入。

**③ 多线程协助扩容**

扩容时用 `sizeCtl` 做协调，一个桶一个桶地迁移；其他线程发现桶已打上迁移标记（`ForwardingNode`），就一起帮忙搬，避免单线程扩容时其他线程干等。

**④ `size()` 用分散计数**

内部维护 `baseCount` + `CounterCell[]` 数组（借鉴 `LongAdder` 思路），不同线程更新不同格子以减少 CAS 冲突。所以 **`size()` 返回的是近似值**，并发修改时可能不准。

**⑤ 不允许 null**

`key` 和 `value` **都不能为 `null`**（会抛 `NullPointerException`），这和 `HashMap` 允许一个 null key 不同。原因是并发环境下 `get` 返回 null 时，无法区分「键不存在」还是「值就是 null」，索性禁止。

#### 2.4.4 和另外几个的对比

| | 线程安全 | 加锁粒度 | 并发性能 | 允许 null |
|---|---|---|---|---|
| `HashMap` | ❌ | 无锁 | —（单线程最快） | ✅ |
| `Hashtable` | ✅ | 整表一把锁（方法级 `synchronized`） | 低 | ❌ |
| `Collections.synchronizedMap` | ✅ | 整表一把锁（包装类） | 低 | ✅ |
| **`ConcurrentHashMap`** | ✅ | 单个桶（CAS + 同步块） | **高** | ❌ |

#### 2.4.5 常用的原子复合方法

把「先查再改」的复合操作做成原子的，避免自己加锁：

```java
ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();

map.putIfAbsent("a", 1);                             // 不存在才放入
map.computeIfAbsent("b", k -> 0);                    // 不存在才计算并放入
map.merge("a", 1, Integer::sum);                     // 存在则合并（这里做累加）
map.replace("a", 2);                                 // 存在才替换
map.compute("a", (k, v) -> v == null ? 1 : v + 1);   // 重新计算
```

典型场景：**计数器**用 `map.merge(key, 1, Integer::sum)` 或 `computeIfAbsent`，比自己 `get` 再 `put`（有竞态）安全得多。

#### 2.4.6 几个坑

- **`size()` 是估计值**，并发场景别拿它做精确判断。
- **复合操作必须用原子方法**：`if (!map.containsKey(k)) map.put(k, v);` 这种写法自己不是原子的，要改用 `putIfAbsent`。
- **迭代器是弱一致的**：遍历时其他线程在改，**不会抛 `ConcurrentModificationException`**，但可能读到旧数据（和 `Iterator` 的 fail-fast 语义不同）。
- `size()` / `isEmpty()` / `containsValue()` 等批量操作都是**弱一致**的。

> 记忆：**ConcurrentHashMap 靠「减小锁粒度」取胜 —— JDK 7 分段锁（16 段），JDK 8 改成 CAS + `synchronized` 锁单个桶；读不加锁靠 `volatile`，写空桶用 CAS、非空桶加锁；扩容多线程协助、计数分散到 CounterCell；不允许 null，`size()` 只是近似值。**

### 2.5 JVM 认知

JVM 重点关注：

- 堆（Heap）
- 栈（Stack）
- 方法区
- GC
- 对象生命周期

### 2.6 反射

```java
import java.lang.reflect.Method;

class UserService {
    public void hello() {
        System.out.println("hello");
    }
}

public class ReflectDemo {
    public static void main(String[] args) throws Exception {
        Class<?> clazz = UserService.class;
        Object obj = clazz.getDeclaredConstructor().newInstance();
        Method method = clazz.getMethod("hello");
        method.invoke(obj);
    }
}
```

### 2.7 Lambda 表达式

**一句话理解**：Lambda 就是「把一小段代码当成参数传进去」。

```java
// 老写法：匿名内部类
Runnable r = new Runnable() {
    @Override
    public void run() {
        System.out.println("跑起来了");
    }
};
r.run();
```

用 Lambda，只留下最关键的「参数」和「要干什么」：

```java
// 新写法：Lambda
Runnable r = () -> System.out.println("跑起来了");
r.run();
```

**语法格式**：

```text
(参数列表) -> { 方法体 }
```

其中 `->` 叫 **Lambda 操作符（箭头）**，读作「参数 → 去做」，左边是输入参数，右边是要执行的代码。例如 `() -> System.out.println("hi")` 就是「没有参数，执行时打印 hi」。

**几种常见简写**（记住这 4 条就够了）：

```java
// 1. 没有参数：空括号 + 箭头
() -> System.out.println("hi");

// 2. 只有一个参数：括号可以省略
x -> System.out.println(x);

// 3. 两个参数：写在一起
(a, b) -> a + b;

// 4. 方法体只有一行：大括号和 return 都能省
x -> x * 2;
```

**为什么要有它？**
因为很多方法需要「传一个操作进去」，比如排序、遍历、过滤。Lambda 让这种写法清爽很多。

```java
import java.util.List;

List<String> names = new ArrayList<>(List.of("Tom", "Jerry", "Bob"));

// 传给 sort：按名字长度从短到长排序
names.sort((a, b) -> a.length() - b.length());
System.out.println(names); // [Tom, Bob, Jerry]
```

### 2.8 Stream 流

**一句话理解**：Stream 就是给集合装一条「流水线」——数据从一头进去，经过一道道加工，从另一头出来。

**最简单的例子**：把名字全变大写。

```java
import java.util.List;
import java.util.stream.Collectors;

List<String> names = List.of("Tom", "Jerry", "Bob");

List<String> upper = names.stream()              // 1. 打开流水线
        .map(name -> name.toUpperCase())         // 2. 每个名字加工一下
        .collect(Collectors.toList());           // 3. 收集成新集合

System.out.println(upper); // [TOM, JERRY, BOB]
```

**流水线上的常见「工序」**：

| 工序 | 作用 | 例子 |
|---|---|---|
| `filter` | 筛选，留下符合条件的 | `.filter(x -> x > 18)` |
| `map` | 一对一转换 | `.map(x -> x * 2)` |
| `sorted` | 排序 | `.sorted()` |
| `distinct` | 去重 | `.distinct()` |
| `limit` | 只取前几个 | `.limit(3)` |
| `skip` | 跳过前几个 | `.skip(1)` |
| `count` | 数个数 | `.count()` |
| `collect` | 收集结果 | `.collect(Collectors.toList())` |
| `forEach` | 逐个处理 | `.forEach(System.out::println)` |

**一个完整的小例子**：

需求：从一群学生里，找出及格的，按分数从高到低排，取前 3 个的名字。

```java
class Student {
    String name;
    int score;

    Student(String name, int score) {
        this.name = name;
        this.score = score;
    }
}
```

```java
List<Student> students = List.of(
        new Student("小明", 59),
        new Student("小红", 95),
        new Student("小刚", 72),
        new Student("小美", 88)
);

List<String> result = students.stream()
        .filter(s -> s.score >= 60)             // 1. 只要及格的
        .sorted((a, b) -> b.score - a.score)    // 2. 按分数从高到低
        .limit(3)                               // 3. 取前 3 个
        .map(s -> s.name)                       // 4. 只要名字
        .collect(Collectors.toList());          // 5. 收集结果

System.out.println(result); // [小红, 小美, 小刚]
```

**三个必须记住的点**：

1. **不改变原集合**：Stream 加工出来的是新结果，原来的 `list` 一点没变。
2. **只能用一次**：一条流用过就「用完」了，想再来一遍要重新 `stream()`。
3. **是「懒」的**：`filter`、`map` 这些中间操作不会立刻执行，等到 `collect`、`forEach` 这类「收尾操作」出现，才真正跑起来。

**记忆口诀**：

- Lambda：把代码当参数传
- Stream：集合的流水线 —— `filter` 挑、`map` 变、`collect` 收

### 2.9 IO 与 NIO（BIO / NIO / AIO）

**一句话理解**：IO = 程序和「外部世界」（文件、网络、设备）交换数据的方式。Java 里分三代：**BIO**（传统 IO，`java.io`）**同步阻塞、面向流**；**NIO**（New IO，Java 1.4，`java.nio`）**同步非阻塞 + 多路复用、面向缓冲区 + 通道**；**AIO**（NIO.2，Java 7）**异步非阻塞、面向回调**。

> 记牢一句话：**BIO 是「一个连接一个线程」；NIO 是「一个线程管一堆连接」。**

**三代对比（核心表）**

| 维度 | BIO | NIO | AIO |
|---|---|---|---|
| 模型 | **同步阻塞** | **同步非阻塞（多路复用）** | **异步非阻塞** |
| 面向 | 流 Stream（单向） | 缓冲区 Buffer + 通道 Channel（双向） | Channel + 回调 |
| 阻塞点 | 每次读 / 写都可能卡住 | 注册到 `Selector`，**就绪才处理** | OS 完成后**回调** |
| 线程模型 | 1 连接 1 线程 | 1 线程管**多个**连接（Reactor） | 回调 / 线程池 |
| 适合 | 连接少、简单 | **高并发**网络（C10K+） | 理论最优，**实际少用** |

**NIO 三大核心组件**

| 组件 | 作用 | 关键点 |
|---|---|---|
| **Channel（通道）** | 数据的「管道」 | **双向**（可读可写），对比流是**单向**的；`FileChannel` / `SocketChannel` / `ServerSocketChannel` |
| **Buffer（缓冲区）** | 数据必须**先读进 Buffer** 再操作 | 核心属性 `position` / `limit` / `capacity`，**`flip()` 切换读模式**（新手最爱忘） |
| **Selector（多路复用器）** | 一个 Selector 监听**多个 Channel** 的事件 | 事件：`OP_ACCEPT` / `OP_CONNECT` / `OP_READ` / `OP_WRITE`；一个线程轮询处理就绪通道 |

> **NIO 快的本质**：BIO 一个线程守一个连接、大部分时间在阻塞等待；NIO 用 `Selector`（底层 Linux 上是 **epoll**）**一个线程轮询成千上万个连接**，只处理「就绪」的，线程数和上下文切换大幅减少。

**几个必须知道的名词**

- **IO 多路复用**：用 `Selector` 让一个线程管理多个连接 —— NIO 的核心，也是 **Netty / Redis / Kafka** 的地基；
- **零拷贝**：`FileChannel.transferTo / transferFrom` 让数据**不进用户态**直接在内核 / 磁盘间搬，Kafka、Netty 大量使用；
- **5 种 IO 模型**（操作系统层面，面试高频）：阻塞 IO、非阻塞 IO、**IO 多路复用**、信号驱动 IO、异步 IO —— 分别对应上面的 BIO / NIO / AIO；
- **AIO 为什么少用**：Linux 上真正的异步 IO 实现不成熟（常用 epoll 模拟），**Netty 也没采用**，高并发主流仍是 **NIO**。

**坑（易混点）**

1. **NIO 的「N」有歧义**：官方是 **New IO**，非阻塞只体现在**网络**部分 —— `FileChannel` 依然**阻塞**；
2. **`ByteBuffer` 读写模式**：读完要 `flip()` 才能读，忘了会**读不到数据 / 读出错**（新手第一大坑）；
3. **`Selector` 空轮询 bug**：某些 JDK 版本 `select()` 会**空转烧 100% CPU**，Netty 专门做了规避（rebuild selector）；
4. **别把 BIO 用在万级连接上**：1 连接 1 线程，线程一多**内存 + 上下文切换**直接爆炸（C10K 问题）。

**选型**

| 场景 | 选谁 |
|---|---|
| 文件读写、连接少、简单网络 | **BIO**（`java.io` 流） |
| **高并发**网络服务 | **NIO + Selector**（或直接用 **Netty**） |
| 想全异步、平台支持 | AIO（了解即可，实战少） |

> 一句话记：**BIO 一连接一线程（阻塞）；NIO 一线程管多连接（`Buffer` + `Channel` + `Selector` 多路复用）；AIO 异步回调但没流行 —— 高并发网络看 NIO / Netty。**

## 4. 学习重点

- 线程安全问题是后端工程师必考内容
- JVM 不是背概念，而是理解对象在内存中的流转
- 集合源码（HashMap、ArrayList）非常关键
- 能看懂源码，才能更好地定位线上问题
- Lambda + Stream 是日常写业务的主力，熟练后代码能短一大截

## 5. 复盘问题

- 创建线程有哪些方式？为什么实际开发推荐用线程池？
- `start()` 和 `run()` 有什么区别？
- `Runnable` 和 `Callable` 有什么不同？
- 线程池为什么能提高性能？
- `scheduleAtFixedRate` 和 `scheduleWithFixedDelay` 有什么区别？分别适合什么场景？
- Fork/Join 的「工作窃取」是什么？它和普通线程池有什么区别？适合什么任务？
- `synchronized` 和 `Lock` 的区别是什么？
- `synchronized` 的锁升级过程是怎样的？偏向锁在什么情况下会撤销？
- 悲观锁和乐观锁的区别是什么？CAS 是什么，它有哪些问题？
- `AtomicInteger` 是怎么保证线程安全的？它和 `synchronized` 有什么区别？
- 什么是 ABA 问题？`AtomicStampedReference` 是怎么解决的？
- `LongAdder` 为什么比 `AtomicLong` 快？
- 为什么用 `AtomicIntegerArray` 而不是 `AtomicInteger[]`？`AtomicStampedReference` 和 `AtomicMarkableReference` 有什么区别？
- `CountDownLatch` 和 `CyclicBarrier` 有什么区别？各自适合什么场景？
- `Semaphore` 和锁有什么区别？为什么 `release()` 不配对 `acquire()` 会出问题？
- 常见的锁可以从哪些维度分类？公平锁和非公平锁该怎么选，读写锁适合什么场景？
- 什么是锁的重入？为什么 `synchronized` 必须可重入？`ReentrantLock` 的重入计数有什么用？
- `Object` 的 `wait/notify` 和 `Condition` 的 `await/signal` 分别有哪些方法？它们有什么区别？
- `LockSupport` 有哪些阻塞 / 唤醒方法？它的许可机制和 `wait/notify` 有什么不同？
- AQS 是什么？它的核心组成、加锁 / 解锁流程是怎样的？`state` 在不同实现类里分别表示什么？
- `HashMap` 为什么线程不安全？
- 什么是 GC Root？
- Lambda 相比匿名内部类，省掉了哪些东西？
- `filter` 和 `map` 分别负责什么？`collect` 为什么不能省？
- BIO、NIO、AIO 有什么区别？NIO 的三大组件是什么？
- 什么是 IO 多路复用？为什么 NIO 更适合高并发？

## 6. 下一步建议

接下来重点进入：

- 计算机网络
- 操作系统
- 数据结构与算法
- MySQL 和 Redis

---

> Java 进阶部分是后端从“能写代码”到“能定位问题”的分水岭。
