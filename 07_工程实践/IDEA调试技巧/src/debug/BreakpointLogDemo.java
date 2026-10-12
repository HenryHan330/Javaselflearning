package debug;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 技巧一：断点处添加 log（Evaluate and Log）
 *
 * 场景：调试时想查看某个中间变量的值，又不想在代码里写 System.out.println，
 *      因为写完容易忘记删，还可能被误提交。
 *
 * 操作步骤：
 *   1. 在 isInterested 方法里 `return i % 2 == 0;` 这一行左侧打一个普通断点；
 *   2. 按住 Shift + 鼠标左键 点击这个断点（或右键断点 -> More），
 *      勾选 "Evaluate and log"，输入框里写：  "interested" + i，这里可以直接写i，本质是随机生成i看是否正确
 *      右键断点breakpoint hit更加清晰
 *   3. 用 Debug 模式运行本类（普通 Run 模式不会打印这些 log）；
 *   4. 观察 Console：每次进入 isInterested 都会打印 interested 和对应的 i。
 *
 * 进阶：勾选 "Breakpoint hit" message 可看到断点命中位置；
 *       勾选 "Stack trace" 可看到当时的调用栈。
 */
public class BreakpointLogDemo {
    public static void main(String[] args) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int count = 0;
        for (int i = 0; i < 5; i++) {
            if (isInterested(random.nextInt(10))) {
                count++;
            }
        }
        System.out.printf("Found %d interested values%n", count);
    }

    private static boolean isInterested(int i) {
        return i % 2 == 0;   // <-- 在这里打普通断点，然后 Shift+左键 勾选 Evaluate and log
    }
}
