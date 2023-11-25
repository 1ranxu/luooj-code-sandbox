package com.luoying.core;

import com.luoying.model.CodeSandBoxCmd;
import com.luoying.template.CodeSandBoxTemplate;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * cpp原生代码沙箱
 */
@Component
public class CppNativeCodeSandBox extends CodeSandBoxTemplate {
    private static final String GLOBAL_CODE_DIR_PATH = "tempCode";
    private static final String PREFIX = "cpp";
    private static final String GLOBAL_CPP_NAME = "main.cpp";

    public CppNativeCodeSandBox() {
        super(GLOBAL_CODE_DIR_PATH, PREFIX, GLOBAL_CPP_NAME);
    }

    @Override
    public CodeSandBoxCmd getCmd(String userCodeParentPath,
                                 String userCodePath) {
        return CodeSandBoxCmd
                .builder()
                .compileCmd(String.format("g++ -finput-charset=UTF-8 -fexec-charset=UTF-8 %s -o %s", userCodePath,
                        userCodePath.substring(0, userCodePath.length() - 4)))
                .runCmd(userCodeParentPath + File.separator +"main")
                .build();
    }
}