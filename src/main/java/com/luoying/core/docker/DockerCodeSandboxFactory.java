package com.luoying.core.docker;

import com.luoying.core.template.DockerCodeSandBoxTemplate;
import com.luoying.model.enums.QuestionSubmitLanguageEnum;

/**
 * @author 落樱的悔恨
 * Docker代码沙箱工厂
 */
public class DockerCodeSandboxFactory {
    /**
     * 根据编程语言获取对应的代码沙箱
     *
     * @param language 编程语言
     */
    public static DockerCodeSandBoxTemplate getInstance(QuestionSubmitLanguageEnum language) {
        if (QuestionSubmitLanguageEnum.JAVA.equals(language)) {// java
            return new JavaDockerCodeSandBox();
        } else if (QuestionSubmitLanguageEnum.CPLUSPLUS.equals(language)) {// cpp
            return new CppDockerCodeSandBox();
        } else if (QuestionSubmitLanguageEnum.PYTHON.equals(language)) {// python
            return new PythonDockerCodeSandBox();
        } else if (QuestionSubmitLanguageEnum.GOLANG.equals(language)) {// go
            return new GoDockerCodeSandBox();
        } else if (QuestionSubmitLanguageEnum.JAVASCRIPT.equals(language)) {// js
            return new JavaScriptDockerCodeSandBox();
        } else if (QuestionSubmitLanguageEnum.RUST.equals(language)) {// rust
            return new RustDockerCodeSandBox();
        } else {
            throw new RuntimeException("暂不支持");
        }
    }
}