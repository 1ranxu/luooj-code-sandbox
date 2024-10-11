package com.luoying.core.docker;

import com.luoying.core.template.DockerCodeSandBoxTemplate;
import com.luoying.model.CodeSandBoxCmd;
import org.springframework.stereotype.Component;

/**
 * @author 落樱的悔恨
 * Java Docker代码沙箱
 */
@Component
public class JavaDockerCodeSandBox extends DockerCodeSandBoxTemplate {
    // 代码文件名
    private static final String CODE_FILE_NAME = "Main.java";

    /**
     * 将参数提供给父类，父类进行具体处理
     */
    public JavaDockerCodeSandBox() {
        super(CODE_FILE_NAME);
    }

    /**
     * 构造 java 编译代码的命令和执行代码的命令
     *
     * @param userCodeParentDirName 用户代码文件父目录的名称
     * @param userCodePath          用户代码文件的绝对路径
     * @return {@link CodeSandBoxCmd}
     */
    @Override
    protected CodeSandBoxCmd getCmd(String userCodeParentDirName, String userCodePath) {
        return CodeSandBoxCmd.builder()
                // javac -encoding utf-8 (路径/文件名.java)
                .compileCmd(String.format("javac -encoding utf-8 %s", userCodePath))
                // java -Xmx256m -Dfile.encoding=UTF-8 -cp (路径) 文件名
                .runCmd("java -Xmx128m -Dfile.encoding=UTF-8 -cp /app/" + userCodeParentDirName + " Main").build();
    }
}
