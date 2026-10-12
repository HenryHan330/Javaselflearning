# 03_计算机基础

## 1. 学习目标

后端开发不只是写业务代码，还需要理解底层运行机制。计算机基础能帮助你更容易理解：

- 网络协议
- 进程与线程
- 内存与 I/O
- 数据结构与算法

## 2. 核心概念

### 2.1 计算机网络

最关键的协议：

- TCP/IP
- HTTP
- Socket
- DNS

```java
// 一个最简单的 HTTP 请求思想：
// 浏览器 -> DNS -> TCP 三次握手 -> HTTP 请求 -> 服务端响应
```

### 2.2 操作系统

重点理解：

- 进程和线程区别
- CPU、内存、IO
- 上下文切换

```text
进程：独立资源单位，拥有自己的内存空间
线程：CPU 执行单元，轻量级执行体
```

### 2.3 数据结构与算法

常见结构：

- 数组
- 链表
- 栈
- 队列
- 哈希表
- 树
- 图

常见思想：

- 哈希
- 双指针
- 动态规划
- 贪心
- 二分

```java
public class BinarySearch {
    public static int binarySearch(int[] arr, int target) {
        int left = 0;
        int right = arr.length - 1;
        while (left <= right) {
            int mid = left + (right - left) / 2;
            if (arr[mid] == target) {
                return mid;
            } else if (arr[mid] < target) {
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        return -1;
    }
}
```

## 3. 学习建议

- 不需要一开始刷大量题目
- 先看懂常见结构和思想
- 在项目中遇到问题时，再回头看算法和网络知识

## 4. 复盘问题

- TCP 三次握手的意义是什么？
- 进程和线程最本质区别是什么？
- 为什么 HashMap 会用哈希？
- 动态规划适合解决什么类型的问题？

## 5. 下一步建议

继续学习：

- MySQL
- Redis
- Spring
- 系统设计

---

> 计算机基础帮助你理解“程序为什么能运行”，而不仅仅是“程序怎么写出来”。
