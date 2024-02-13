package com.luoying.core.docker;

import com.luoying.model.CodeSandBoxCmd;
import com.luoying.core.template.DockerCodeSandBoxTemplate;
import org.springframework.stereotype.Component;

/**
 * Java原生代码沙箱
 */
@Component
public class JavaDockerCodeSandBox extends DockerCodeSandBoxTemplate {
    // 顶级目录（相对于当前项目）
    private static final String GLOBAL_CODE_DIR_PATH = "tempCode";

    // 二级目录（用于区分编程语言）（相对于当前项目）
    private static final String PREFIX = "java";

    // 代码文件名
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    // 镜像名称
    private static final String IMAGE_NAME = "openjdk:8-alpine";

    // 容器名称
    private static final String CONTAINER_NAME = "java_code_sandbox";

    /**
     * 将参数提供给父类，父类进行具体处理
     */
    public JavaDockerCodeSandBox() {
        super(GLOBAL_CODE_DIR_PATH, PREFIX, GLOBAL_JAVA_CLASS_NAME, IMAGE_NAME, CONTAINER_NAME);
    }

    /**
     * 构造java编译代码的命令和执行代码的命令
     *
     * @param UUID         目录
     * @param userCodePath 用户代码文件的绝对路径
     * @return
     */
    @Override
    protected CodeSandBoxCmd getCmd(String UUID, String userCodePath) {
        return CodeSandBoxCmd
                .builder()
                // javac -encoding utf-8 (路径/文件名.java)
                .compileCmd(String.format("javac -encoding utf-8 %s", userCodePath))
                // java -Xmx256m -Dfile.encoding=UTF-8 -cp (路径) 文件名
                .runCmd("java -Xmx256m -Dfile.encoding=UTF-8 -cp /app/" + UUID + " Main")
                .build();
    }
}
