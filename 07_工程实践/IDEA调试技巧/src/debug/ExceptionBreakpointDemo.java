package debug;

/**
 * 技巧三：异常断点（Exception Breakpoints）
 *
 * 场景：希望程序停在「抛出异常之前」的位置，方便查看当时的变量信息。
 *
 * 操作步骤：
 *   1. 先在 catch (NumberFormatException e) 这一行打一个普通断点，
 *      用 Debug 模式运行，确认异常类型是 NumberFormatException；（鼠标悬停在31行e上看异常类型）
 *   2. 打开 Breakpoints 窗口（快捷键 Cmd+Shift+F8 / Ctrl+Shift+F8）不管用点击左下方两个重叠的红色圆，
 *      点左上角 "+" -> "Java Exception Breakpoints"，
 *      输入 NumberFormatException 并确定；
 *   3. 重新用 Debug 模式运行本类；
 *   4. 程序会直接停在 Integer.parseInt 抛出异常的那一行
 *      （落在 JDK 源码里，往上翻调用栈就能回到 process 方法），
 *      并在 Variables 里看到当时的变量值。
 */
public class ExceptionBreakpointDemo {
    public static void main(String[] args) {
        String[] inputs = {"123", "456", "abc", "789"};
        for (String s : inputs) {
            process(s);
        }
    }

    private static void process(String s) {
        try {
            int value = Integer.parseInt(s);   // <-- 异常断点会停在这一行（处理 "abc" 时）
            System.out.println("解析成功: " + value);
        } catch (NumberFormatException e) {    // <-- 第一步：先在这里打普通断点，确认异常类型
            System.out.println("解析失败: " + s);
        }
    }
}
