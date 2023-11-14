/**
 * 无限睡眠（阻塞程序执行）
 */
public class Main {
    private static final long ONE_HOUR = 60 * 60 * 1000;

    public static void main(String[] args) throws Exception {
        Thread.sleep(ONE_HOUR);
    }
}