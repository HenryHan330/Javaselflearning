# IDEA 调试技巧

> 位置：`07_工程实践/IDEA调试技巧`。这是**我自己的学习笔记**，记录 IDEA 里几种常用断点的用法。
> 每一条都配了一个最小可运行的 demo（在 `src/debug/` 下），建议自己敲一遍、Debug 跑一遍。

## 1. 学习目标

- **少写垃圾代码**：调试时不靠 `System.out.println` 打印，避免忘删、误提交。
- **会追踪写入**：知道某个字段到底在哪些地方被改了值。
- **会定位分支**：接口有多个实现、或多线程场景下，弄清运行期实际走了哪条路。
- **会断异常**：在异常**抛出之前**停下，看当时的现场变量。

## 2. 四种断点速览

- **行断点（普通断点）**：最常用，程序执行到这一行就停。
- **字段断点（Field Watchpoint）**：某个字段被读/写时停，用来追“谁改了我的值”。
- **方法断点（Method Breakpoint）**：进入/离开某个方法时停，常用于接口多实现。
- **异常断点（Exception Breakpoint）**：抛出指定异常时停，停在抛出点而不是 catch 点。

## 3. 技巧一：断点处加 log（Evaluate and Log）

**场景**：调试时想看某个中间变量的值，又不想在代码里写 `System.out.println`——写完容易忘删，还可能误提交。

**操作步骤**：

1. 在目标行左侧打一个普通断点（例：`isInterested` 方法里的 `return i % 2 == 0;`）。
2. 按住 **Shift + 鼠标左键** 点这个断点（或右键断点 → More），勾选 **Evaluate and log**，输入框里写要打印的表达式，例如 `"interested" + i`。
3. 用 **Debug** 模式运行（普通 Run 不会打印这些 log）。
4. 看 Console：每次进入该方法都会打印一次，但**代码本身没有任何改动**。

**进阶**：勾 **Breakpoint hit** 能看到断点命中位置；勾 **Stack trace** 能看到当时的调用栈。

**demo**：`src/debug/BreakpointLogDemo.java`

## 4. 技巧二：字段断点（Field Watchpoint）

**场景**：想弄清某个字段的值到底在哪里被改掉，逐层追调用栈又慢又容易漏。

**操作步骤**：

1. 在**字段定义处**左侧点断点，图标会变成一只「眼睛」。
2. 右键眼睛图标，勾 **Field modification**（想连读取也停就再勾 **Field access**）。
3. 用 Debug 模式运行。
4. 程序会在**每一个修改该字段的地方**自动停下。按继续（绿色三角 ▶ / 快捷键 F9）会走到下一个修改点，依次经过各个方法。

**概念区分**：

- **Field modification（字段修改）**：字段被赋值/写入时触发，比如 `balance = x`、`balance += x`。这是字段断点的核心用途。
- **Field access（字段访问）**：字段被读取但本身没变时触发，比如 `System.out.println(balance)`、`return balance`。

**进阶**：修改点太多时，在弹窗 **Condition** 里写条件，比如 `amount > 100`，只关心金额较大的改动。

**demo**：`src/debug/FieldBreakpointDemo.java`

## 5. 技巧三：异常断点（Exception Breakpoint）

**场景**：希望程序停在**抛出异常之前**的位置，方便查看当时的变量信息。

**操作步骤**：

1. 先在 `catch (...)` 那一行打普通断点、Debug 跑一次，确认异常的具体类型（例：`NumberFormatException`）。鼠标悬停在 catch 的异常变量上能看到类型。
2. 打开 **Breakpoints** 窗口（快捷键 **Cmd+Shift+F8** / **Ctrl+Shift+F8**；也可以点左下角两个重叠的红色圆），点左上角 **+ → Java Exception Breakpoints**，输入异常类型并确定。
3. 重新 Debug 运行。
4. 程序会直接停在 `Integer.parseInt` 抛出异常的那一行（会落进 JDK 源码里），往上翻调用栈就能回到自己的方法，并在 Variables 里看到当时的变量值。

**demo**：`src/debug/ExceptionBreakpointDemo.java`

## 6. 技巧四：方法断点（Method Breakpoint）

**场景**：一个接口有多个实现类，调试时想知道运行期到底走进了哪个实现。

**操作步骤**：

1. 在**接口方法定义处**左侧点断点，图标会变成「菱形 ♦」（方法断点）。
2. （可选）右键断点，在 **Condition** 里写自定义条件。
3. 用 Debug 模式运行。
4. 程序会自动停到**实际被调用的实现类**的方法里，观察进入的是哪一个实现。

**Condition 能写什么**：进入方法时能访问到的 Java 布尔表达式——方法参数、`this`（当前对象）、字段。

- 无参数时最常用 `this` 区分实现类，例如 `this instanceof ChineseGreeter`。
- 有参数时可以直接写参数条件，例如 `name.equals("张三")`、`name != null`。

**提示**：同理可用于 `Runnable` 接口的 `run` 方法，调试多线程时非常好用。

**demo**：`src/debug/MethodBreakpointDemo.java`

## 7. 代码示例

4 个 demo 都是最小可运行的，都在 `src/debug/` 下，包名为 `debug`：

- `src/debug/BreakpointLogDemo.java` —— 技巧一：Evaluate and Log
- `src/debug/FieldBreakpointDemo.java` —— 技巧二：字段断点
- `src/debug/ExceptionBreakpointDemo.java` —— 技巧三：异常断点
- `src/debug/MethodBreakpointDemo.java` —— 技巧四：方法断点

跑法：用 IDEA 打开本目录，右键对应类 → **Debug**（不是 Run）。每个类的 Javadoc 注释里都写了该 demo 的具体打断点位置。

## 8. 重点复盘

- 调试时**优先用断点能力代替打印**：Evaluate and log 不污染代码，是 `println` 的干净替代。
- **字段断点**用来追“谁改了值”，注意区分 **modification（写）** 和 **access（读）**。
- **方法断点**专治「接口多实现、不知道走了哪个」，菱形图标是它的标志。
- **异常断点**要停在抛出点、而不是 catch 点，才能看到真正的现场。
- 四种断点都支持 **Condition**，条件写对能大幅减少无效停顿。
- 记住 Breakpoints 窗口的快捷键 **Cmd+Shift+F8**（Mac）/ **Ctrl+Shift+F8**（Win），统一管理所有断点。
