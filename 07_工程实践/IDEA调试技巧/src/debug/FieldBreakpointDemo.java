package debug;

/**
 * 技巧二：字段断点（Field Watchpoint）
 *
 * 场景：阅读源码时想知道某个字段的值到底在哪些地方被修改了，字段有修改时就会跳转到相应方法位置
 *      逐层追调用栈又慢又容易漏。
 *
 * 操作步骤：
 *   1. 在 BankAccount 类的 `double balance;` 字段定义处左侧点击打一个断点，
 *      断点会变成「眼睛」图标；是在字段定义的地方打断点
 *   2. 右键这个眼睛图标，勾选 "Field modification"
 *      （想同时看读取操作就再勾选 "Field access"）；
 *   3. 用 Debug 模式运行本类；
 *   4. 程序会在每一个修改 balance 的地方自动停下，看它停在哪个方法里。
 *注意：现在按 继续（绿色三角 ▶，快捷键 F9）是下方的l箭头不是右上角的，程序会自动停到下一个修改 balance 的地方，依次是：
 *
 * deposit(500) 里的 balance += amount
 * withdraw(200)
 * chargeFee(10)
 * deposit(50)
 * 概念：
 *   - Field modification（字段修改）：字段的值被「赋值/写入」时触发，比如 balance = x、balance += x。
 *     勾选后程序会在每一个修改 balance 的地方停下，这是技巧②的核心用途。
 *   - Field access（字段访问/读取）：字段的值被「读取/使用」但本身没变时触发，
 *     比如 System.out.println(balance)、return balance。
 *   两个可以同时勾选：不管读还是写，只要碰到该字段就停。
 *
 * 进阶：修改字段的地方很多时，可以在弹窗的 Condition 里写条件，
 *       比如 amount > 100，只关心金额较大的改动。
 */
public class FieldBreakpointDemo {
    public static void main(String[] args) {
        BankAccount account = new BankAccount(1000);
        account.deposit(500);
        account.withdraw(200);
        account.chargeFee(10);
        account.deposit(50);
        System.out.println("最终余额: " + account.balance);
    }
}

class BankAccount {
    double balance;   // <-- 在这里打字段断点（眼睛图标）

    BankAccount(double balance) {
        this.balance = balance;
    }

    void deposit(double amount) {
        balance += amount;
    }

    void withdraw(double amount) {
        balance -= amount;
    }

    void chargeFee(double amount) {
        balance -= amount;
    }
}
