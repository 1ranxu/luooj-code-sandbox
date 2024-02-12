package com.luoying;

import com.luoying.core.CppNativeCodeSandBox;
import com.luoying.core.JavaNativeCodeSandBox;
import com.luoying.model.ExecuteCodeRequest;
import com.luoying.model.ExecuteCodeResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.management.*;
import java.util.Arrays;
import java.util.List;

@SpringBootTest
@Slf4j
class LuoojCodeSandboxApplicationTests {

    @Test
    void testMemoryMXBean() {
        // 获取MemoryMXBean
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        // 打印：堆内存使用
        log.info("heapMemoryUsage: {}", memoryMXBean.getHeapMemoryUsage().toString());
        // 打印：非堆内存使用
        log.info("nonHeapMemoryUsage: {}", memoryMXBean.getNonHeapMemoryUsage().toString());
    }

    @Test
    void testMemoryManagerMXBean() {
        // 获取所有内存管理器MXBean列表，并遍历
        List<MemoryManagerMXBean> memoryManagerMXBeans = ManagementFactory.getMemoryManagerMXBeans();
        for (MemoryManagerMXBean memoryManagerMXBean : memoryManagerMXBeans) {
            // 获取管理器名称
            String name = memoryManagerMXBean.getName();
            // 获取此内存管理器管理的内存池名称
            String[] memoryPoolNames = memoryManagerMXBean.getMemoryPoolNames();
            log.info("memoryManagerName: {}, memoryPoolNames: {}", name, memoryPoolNames);
        }
    }

    @Test
    void testMemoryPoolMXBean() {
        // 获取所有内存池MXBean列表，并遍历
        List<MemoryPoolMXBean> memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
        for (MemoryPoolMXBean memoryPoolMXBean : memoryPoolMXBeans) {
            // 内存分区名
            String name = memoryPoolMXBean.getName();
            // 内存管理器名称
            String[] memoryManagerNames = memoryPoolMXBean.getMemoryManagerNames();
            // 内存分区类型
            MemoryType type = memoryPoolMXBean.getType();
            // 内存使用情况
            MemoryUsage usage = memoryPoolMXBean.getUsage();
            // 内存使用峰值情况
            MemoryUsage peakUsage = memoryPoolMXBean.getPeakUsage();
            // 打印
            log.info(name + ":");
            log.info("    managers: {}", memoryManagerNames);
            log.info("    type: {}", type.toString());
            log.info("    usage: {}", usage.toString());
            log.info("    peakUsage: {}", peakUsage.toString());
            log.info("-----------------------------------------");
        }

    }

    static MemoryUsage usage;
    static MemoryUsage peakUsage;

    @Test
    void testCodeCacheManager() {
        MemoryPoolMXBean memoryPoolMXBean = ManagementFactory.getMemoryPoolMXBeans().get(0);
        // 内存分区名
        String name = memoryPoolMXBean.getName();
        // 内存管理器名称
        String[] memoryManagerNames = memoryPoolMXBean.getMemoryManagerNames();
        // 内存分区类型
        MemoryType type = memoryPoolMXBean.getType();
        // 内存使用情况
        MemoryUsage usage = memoryPoolMXBean.getUsage();
        // 内存使用峰值情况
        MemoryUsage peakUsage = memoryPoolMXBean.getPeakUsage();
        // 打印
        log.info(name + ":");
        log.info("    managers: {}", memoryManagerNames);
        log.info("    type: {}", type.toString());
        log.info("    usage: {}", usage.toString());
        log.info("    peakUsage: {}", peakUsage.toString());
        log.info("-----------------------------------------");
    }


    @Test
    void test1() {
        JavaNativeCodeSandBox javaNativeCodeSandBox = new JavaNativeCodeSandBox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("abcs4d5s4d6s5d465a4da4d8s7d8s7d8a7da8d7a89s7d48as4d8a4d8as74d8as7d89asd1sa1da6s4da87sd87a89s7da897sd89as7d89a7d89sa7da89d7a89d7a89d7a89s7da89sd7a89s7da897da89d7a87da89d7a897sd8a7s8d9a7d897a89d7a89s7d8a7sd8as7da8s7da87d89a7sd89a7sd897sa8d97a8s9d7sa8d7a89s7d89sa7da89sd789sa7da8s7da8s979a7sda8s7d89as7da89s7d89as7d89as7d8sa97d89a7d89sad79a8s7d89as7da89d78as97d8a7d8a7d89asd7a9s8d7as89d7a8s9d7a89sd78sa7d8s7da89s7da8s9d78as7da89s7d8sa7da8s7da9sda9sd78a8sd78a9sd78s7ad9d7a98s7da897d89a7da987d8s9a7da8s9d78a9s7da89s7d97da89s7d8as7d8as7da8s97d9s7da89s7da8s97da89s7d8as9d7a8s9d7as8d"));
        executeCodeRequest.setCode("\n" +
                "import java.util.*;\n" +
                "\n" +
                "\n" +
                "public class Main{\n" +
                "    public static void main(String[] args){\n" +
                "        Scanner sc = new Scanner(System.in);\n" +
                "        String s = sc.nextLine();\n" +
                "        char[] sin = s.toCharArray();\n" +
                "        int count = 0;  //用来计算统计数字的个数\n" +
                "        int size1 = sin.length;\n" +
                "        for (int i = 0; i < size1; i++){\n" +
                "            if (sin[i] >= '0' && sin[i] <= '9'){\n" +
                "                count++;\n" +
                "            }\n" +
                "        }\n" +
                "        int size2 = size1 + count * 5;\n" +
                "        char[] res = new char[size2];\n" +
                "        for(int i = size2 - 1, j = size1 - 1; i >= 0; i--, j--){\n" +
                "            if (sin[j] > '9' || sin[j] < '0'){\n" +
                "                res[i] = sin[j];\n" +
                "            }\n" +
                "            else{\n" +
                "                res[i] = 'r';\n" +
                "                res[i-1] = 'e';\n" +
                "                res[i-2] = 'b';\n" +
                "                res[i-3] = 'm';\n" +
                "                res[i-4] = 'u';\n" +
                "                res[i-5] = 'n';\n" +
                "                i -= 5;\n" +
                "            }\n" +
                "        }\n" +
                "        String ans = new String(res);\n" +
                "        System.out.println(ans);\n" +
                "    } \n" +
                "}");
        executeCodeRequest.setLanguage("java");
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandBox.executeCode(executeCodeRequest);
        log.info(executeCodeResponse.toString());
    }

    @Test
    void test2() {
        CppNativeCodeSandBox cppNativeCodeSandBox = new CppNativeCodeSandBox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("abcs4d5s4d6s5d465a4da4d8s7d8s7d8a7da8d7a89s7d48as4d8a4d8as74d8as7d89asd1sa1da6s4da87sd87a89s7da897sd89as7d89a7d89sa7da89d7a89d7a89d7a89s7da89sd7a89s7da897da89d7a87da89d7a897sd8a7s8d9a7d897a89d7a89s7d8a7sd8as7da8s7da87d89a7sd89a7sd897sa8d97a8s9d7sa8d7a89s7d89sa7da89sd789sa7da8s7da8s979a7sda8s7d89as7da89s7d89as7d89as7d8sa97d89a7d89sad79a8s7d89as7da89d78as97d8a7d8a7d89asd7a9s8d7as89d7a8s9d7a89sd78sa7d8s7da89s7da8s9d78as7da89s7d8sa7da8s7da9sda9sd78a8sd78a9sd78s7ad9d7a98s7da897d89a7da987d8s9a7da8s9d78a9s7da89s7d97da89s7d8as7d8as7da8s97d9s7da89s7da8s97da89s7d8as9d7a8s9d7as8d"));
        executeCodeRequest.setCode("#include <algorithm>\n" +
                "#include <iostream>\n" +
                "#include <string>\n" +
                "\n" +
                "int main() {\n" +
                "  std::string number = \"number\";\n" +
                "  std::string s;\n" +
                "  std::cin >> s;\n" +
                "\n" +
                "  int n = static_cast<int>(s.length());\n" +
                "\n" +
                "  size_t count = std::count_if(s.begin(), s.end(),\n" +
                "                               [](const char c) { return std::isdigit(c); });\n" +
                "\n" +
                "  s.resize(n + count * 5);\n" +
                "  int new_n = static_cast<int>(s.length());\n" +
                "  int i = n-1, j = new_n-1;\n" +
                "  // DBG(i, j);\n" +
                "\n" +
                "  while (i >= 0) {\n" +
                "    auto c = s[i];\n" +
                "    if (std::isdigit(c)) {\n" +
                "      for (int k = j, idx = 5; idx >= 0; k--, idx--) {\n" +
                "        // DBG(k, idx);\n" +
                "        s[k] = number[idx];\n" +
                "      }\n" +
                "      j -= 6;\n" +
                "    } else {\n" +
                "      s[j] = c;\n" +
                "      j--;\n" +
                "    }\n" +
                "    i--;\n" +
                "  }\n" +
                "\n" +
                "  std::cout << s << std::endl;\n" +
                "  return 0;\n" +
                "}");
        executeCodeRequest.setLanguage("c++");
        ExecuteCodeResponse executeCodeResponse = cppNativeCodeSandBox.executeCode(executeCodeRequest);
        log.info(executeCodeResponse.toString());
    }

    @Test
    void test3() {
        JavaNativeCodeSandBox javaNativeCodeSandBox = new JavaNativeCodeSandBox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2"));
        executeCodeRequest.setCode("import java.util.Scanner;\n" +
                "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        Scanner scanner = new Scanner(System.in);\n" +
                "        int a = scanner.nextInt();\n" +
                "        int b = scanner.nextInt();\n" +
                "        System.out.println(a + b);\n" +
                "    }\n" +
                "}");
        executeCodeRequest.setLanguage("java");
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandBox.executeCode(executeCodeRequest);
        log.info(executeCodeResponse.toString());
    }

    @Test
    void test4() {
        CppNativeCodeSandBox cppNativeCodeSandBox = new CppNativeCodeSandBox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2"));
        executeCodeRequest.setCode("#include<iostream>\n" +
                "int main(){\n" +
                "    int a,b;\n" +
                "    std::cin >> a >> b;\n" +
                "    int sum = a + b;\n" +
                "    std::cout << sum << std::endl;\n" +
                "    return 0;\n" +
                "}");
        executeCodeRequest.setLanguage("c++");
        ExecuteCodeResponse executeCodeResponse = cppNativeCodeSandBox.executeCode(executeCodeRequest);
        log.info(executeCodeResponse.toString());
    }

    @Test
    void test5() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        long start = memoryMXBean.getNonHeapMemoryUsage().getUsed() + memoryMXBean.getHeapMemoryUsage().getUsed();
        log.info("start: {}", start);
        test3();
        long end = memoryMXBean.getNonHeapMemoryUsage().getUsed() + memoryMXBean.getHeapMemoryUsage().getUsed();
        log.info("end: {}", end);
        log.info("cost: {}", (double)(end - start) / 1024 / 1024);
    }

    @Test
    void test6() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        long start = memoryMXBean.getNonHeapMemoryUsage().getUsed() + memoryMXBean.getHeapMemoryUsage().getUsed();
        log.info("start: {}", start);
        test4();
        long end = memoryMXBean.getNonHeapMemoryUsage().getUsed() + memoryMXBean.getHeapMemoryUsage().getUsed();
        log.info("end: {}", end);
        log.info("cost: {}", (double)(end - start) / 1024 / 1024);
    }

}
