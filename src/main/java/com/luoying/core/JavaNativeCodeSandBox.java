package com.luoying.core;

import com.luoying.model.CodeSandBoxCmd;
import com.luoying.template.CodeSandBoxTemplate;
import org.springframework.stereotype.Component;

/**
 * java原生代码沙箱
 */
@Component
public class JavaNativeCodeSandBox extends CodeSandBoxTemplate {
    private static final String GLOBAL_CODE_DIR_PATH = "tempCode";

    private static final String PREFIX = "java";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    public JavaNativeCodeSandBox() {
        super(GLOBAL_CODE_DIR_PATH, PREFIX, GLOBAL_JAVA_CLASS_NAME);
    }

    @Override
    protected CodeSandBoxCmd getCmd(String userCodeParentPath, String userCodePath) {
        return CodeSandBoxCmd
                .builder()
                .compileCmd(String.format("javac -encoding utf-8 %s", userCodePath))
                .runCmd(String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main", userCodeParentPath))
                .build();
    }
}
