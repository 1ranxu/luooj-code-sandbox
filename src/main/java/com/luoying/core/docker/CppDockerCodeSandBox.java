package com.luoying.core.docker;

import com.luoying.core.template.DockerCodeSandBoxTemplate;
import com.luoying.model.CodeSandBoxCmd;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * @author 落樱的悔恨
 * Cpp Docker代码沙箱
 */
@Component
public class CppDockerCodeSandBox extends DockerCodeSandBoxTemplate {
    // 代码文件名
    private static final String CODE_FILE_NAME = "main.cpp";

    /**
     * 将参数提供给父类，父类进行具体处理
     */
    public CppDockerCodeSandBox() {
        super(CODE_FILE_NAME);
    }

    /**
     * 构造 cpp 编译代码的命令和执行代码的命令
     *
     * @param userCodeParentDirName 用户代码文件父目录的名称
     * @param userCodePath          用户代码文件的绝对路径
     * @return {@link CodeSandBoxCmd}
     */
    @Override
    public CodeSandBoxCmd getCmd(String userCodeParentDirName, String userCodePath) {
        return CodeSandBoxCmd.builder()
                // g++ -finput-charset=UTF-8 -fexec-charset=UTF-8 (路径/文件名.cpp) -o (路径/文件名)
                // 第一个参数用于定位编译的文件，第二个参数用于指明编译后的文件名以及存放的位置
                .compileCmd(String.format("g++ -finput-charset=UTF-8 -fexec-charset=UTF-8 %s -o %s", userCodePath, userCodePath.substring(0, userCodePath.length() - 4)))
                // 文件名.exe(编译后得到的可执行文件)，Windows下可以双击运行，也可以在命令行中输入：路径/文件名.exe 运行
                // 这里采用的是 Windows 和 Linux 都可以运行的方式：路径/文件名
                .runCmd("/app/" + userCodeParentDirName + File.separator + "main").build();
    }
}
