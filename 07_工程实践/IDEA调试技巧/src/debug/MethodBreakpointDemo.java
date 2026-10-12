package debug;

/**
 * 技巧四：方法断点（Method Breakpoints）
 *
 * 场景：接口的一个方法被多个实现类实现，调试时想知道运行期到底走进哪个实现。
 *
 * 操作步骤：
 *   1. 在 Greeter 接口的 `void greet();` 方法定义处左侧点击打一个断点，
 *      断点会变成「菱形 ♦」形状（方法断点）；
 *   2. （可选）右键该断点，在 Condition 里自定义跳转条件；
 *   3. 用 Debug 模式运行本类；
 *   4. 程序会自动停到「实际被调用的实现类」的 greet 方法里，
 *      观察它进了 ChineseGreeter 还是 EnglishGreeter。
 *
 * Condition 可以写什么：
 *   进入方法时能访问到的 Java 布尔表达式：方法参数、this（当前对象）、字段。
 *   本例 greet() 没有参数，最常用 this 区分实现类：
 *     this instanceof ChineseGreeter   // 只在中文实现停
 *     this instanceof EnglishGreeter   // 只在英文实现停
 *   若方法有参数，还能写参数条件，比如 name.equals("张三")、name != null。
 *
 * 提示：同理可用于 Runnable 接口的 run 方法，调试多线程时非常好用。
 */
public class MethodBreakpointDemo {
    public static void main(String[] args) {
        Greeter[] greeters = {new ChineseGreeter(), new EnglishGreeter()};
        for (Greeter g : greeters) {
            g.greet();
        }
    }
}

interface Greeter {
    void greet();   // <-- 在这里打方法断点（菱形 ♦）
}

class ChineseGreeter implements Greeter {
    @Override
    public void greet() {
        System.out.println("你好，世界！");
    }
}

class EnglishGreeter implements Greeter {
    @Override
    public void greet() {
        System.out.println("Hello, world!");
    }
}
