package com.luoying.core.nativ;

import com.luoying.model.CodeSandBoxCmd;
import com.luoying.core.template.NativeCodeSandBoxTemplate;
import org.springframework.stereotype.Component;

/**
 * @author 落樱的悔恨
 * java原生代码沙箱
 */
@Component
public class JavaNativeCodeSandBox extends NativeCodeSandBoxTemplate {
    // 顶级目录（相对于当前项目）
    private static final String GLOBAL_CODE_DIR_PATH = "tempCode";
    // 二级目录（用于区分编程语言）（相对于当前项目）
    private static final String PREFIX = "java";
    // 代码文件名
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    /**
     * 将参数提供给父类，父类进行具体处理
     */
    public JavaNativeCodeSandBox() {
        super(GLOBAL_CODE_DIR_PATH, PREFIX, GLOBAL_JAVA_CLASS_NAME);
    }

    /**
     * 构造java编译代码的命令和执行代码的命令
     *
     * @param userCodeParentPath 用户代码父目录的绝对路径
     * @param userCodePath       用户代码文件的绝对路径
     * @return
     */
    @Override
    protected CodeSandBoxCmd getCmd(String userCodeParentPath, String userCodePath) {
        return CodeSandBoxCmd
                .builder()
                // javac -encoding utf-8 (路径/文件名.java)
                .compileCmd(String.format("javac -encoding utf-8 %s", userCodePath))
                // java -Xmx256m -Dfile.encoding=UTF-8 -cp (路径) 文件名
                .runCmd(String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main", userCodeParentPath))
                .build();
    }
}
