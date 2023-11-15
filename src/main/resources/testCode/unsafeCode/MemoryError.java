import java.util.ArrayList;
import java.util.List;

/**
 * 无限占用系统内存
 */
public class Main {
    public static void main(String[] args) {
        List<byte[]> bytesList = new ArrayList<>();
        while (true) {
            bytesList.add(new byte[1024]);
        }
    }
}