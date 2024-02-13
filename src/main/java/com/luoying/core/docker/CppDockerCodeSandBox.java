package com.luoying.core.docker;

import com.luoying.model.CodeSandBoxCmd;
import com.luoying.core.template.DockerCodeSandBoxTemplate;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * C++原生代码沙箱
 */
@Component
public class CppDockerCodeSandBox extends DockerCodeSandBoxTemplate {
    // 顶级目录（相对于当前项目）
    private static final String GLOBAL_CODE_DIR_PATH = "tempCode";

    // 二级目录（用于区分编程语言）（相对于当前项目）
    private static final String PREFIX = "c++";

    // 代码文件名
    private static final String GLOBAL_JAVA_CLASS_NAME = "main.cpp";

    // 镜像名称
    private static final String IMAGE_NAME = "alpine";

    // 容器名称
    private static final String CONTAINER_NAME = "cpp_code_sandbox";

    /**
     * 将参数提供给父类，父类进行具体处理
     */
    public CppDockerCodeSandBox() {
        super(GLOBAL_CODE_DIR_PATH, PREFIX, GLOBAL_JAVA_CLASS_NAME,IMAGE_NAME,CONTAINER_NAME);
    }

    /**
     * 构造c++编译代码的命令和执行代码的命令
     *
     * @param userCodeParentPath 用户代码父目录的绝对路径
     * @param userCodePath       用户代码文件的绝对路径
     * @return
     */
    @Override
    public CodeSandBoxCmd getCmd(String userCodeParentPath, String userCodePath) {
        return CodeSandBoxCmd
                .builder()
                // g++ -finput-charset=UTF-8 -fexec-charset=UTF-8 (路径/文件名.cpp) -o (路径/文件名)
                // 第一个参数用于定位编译的文件，第二个参数用于指明编译后的文件名以及存放的位置
                .compileCmd(String.format("g++ -finput-charset=UTF-8 -fexec-charset=UTF-8 %s -o %s", userCodePath,
                        userCodePath.substring(0, userCodePath.length() - 4)))
                // 文件名.exe(编译后得到的可执行文件)，Windows下可以双击运行，也可以在命令行中输入：路径/文件名.exe 运行
                // 这里采用的是 Windows 和 Linux 都可以运行的方式：路径/文件名
                .runCmd("/app" + File.separator + "main")
                .build();
    }


}
