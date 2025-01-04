package com.luoying.core.docker;

import com.luoying.core.template.DockerCodeSandBoxTemplate;
import com.luoying.model.CodeSandBoxCmd;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * @author 落樱的悔恨
 * Go Docker代码沙箱
 */
@Component
public class GoDockerCodeSandBox extends DockerCodeSandBoxTemplate {
    // 代码文件名
    private static final String CODE_FILE_NAME = "main.go";

    /**
     * 将参数提供给父类，父类进行具体处理
     */
    public GoDockerCodeSandBox() {
        super(CODE_FILE_NAME);
    }

    /**
     * 构造 go 编译代码的命令和执行代码的命令
     *
     * @param userCodeParentDirName 用户代码文件父目录的名称
     * @param userCodePath          用户代码文件的绝对路径
     * @return {@link CodeSandBoxCmd}
     */
    @Override
    public CodeSandBoxCmd getCmd(String userCodeParentDirName, String userCodePath) {
        return CodeSandBoxCmd.builder()
                // go build (路径/文件名.go)
                .compileCmd(String.format("go build -o %s %s", userCodePath.substring(0, userCodePath.length() - 7), userCodePath))
                // ./文件名
                .runCmd("/app/" + userCodeParentDirName + File.separator + "main").build();
    }
}
