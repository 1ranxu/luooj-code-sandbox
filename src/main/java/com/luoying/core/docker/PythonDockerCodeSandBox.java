package com.luoying.core.docker;

import com.luoying.core.template.DockerCodeSandBoxTemplate;
import com.luoying.model.CodeSandBoxCmd;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * @author 落樱的悔恨
 * Python Docker代码沙箱
 */
@Component
public class PythonDockerCodeSandBox extends DockerCodeSandBoxTemplate {
    // 代码文件名
    private static final String CODE_FILE_NAME = "main.py";

    /**
     * 将参数提供给父类，父类进行具体处理
     */
    public PythonDockerCodeSandBox() {
        super(CODE_FILE_NAME);
    }

    /**
     * 构造 python 编译代码的命令和执行代码的命令
     *
     * @param userCodeParentDirName 用户代码文件父目录的名称
     * @param userCodePath          用户代码文件的绝对路径
     * @return {@link CodeSandBoxCmd}
     */
    @Override
    public CodeSandBoxCmd getCmd(String userCodeParentDirName, String userCodePath) {
        return CodeSandBoxCmd.builder()
                .compileCmd(null)
                // python3 /app/路径/文件名.py
                .runCmd("python3 /app/" + userCodeParentDirName + File.separator + CODE_FILE_NAME).build();
    }
}
