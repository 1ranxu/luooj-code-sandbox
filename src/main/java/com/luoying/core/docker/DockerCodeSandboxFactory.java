package com.luoying.core.docker;

import com.luoying.core.nativ.CppNativeCodeSandBox;
import com.luoying.core.nativ.JavaNativeCodeSandBox;
import com.luoying.core.template.DockerCodeSandBoxTemplate;
import com.luoying.model.enums.QuestionSubmitLanguageEnum;
import com.luoying.core.template.NativeCodeSandBoxTemplate;

/**
 * @author 落樱的悔恨
 * 代码沙箱工厂
 */
public class DockerCodeSandboxFactory {
    /**
     * 根据编程语言获取对应的代码沙箱
     * @param language
     */
    public static DockerCodeSandBoxTemplate getInstance(QuestionSubmitLanguageEnum language) {
         if (QuestionSubmitLanguageEnum.JAVA.equals(language)){// java
             return new JavaDockerCodeSandBox();
         } else if (QuestionSubmitLanguageEnum.CPLUSPLUS.equals(language)) {// c++
             return new CppDockerCodeSandBox();
         } else {
             throw new RuntimeException("暂不支持");
         }
    }
}