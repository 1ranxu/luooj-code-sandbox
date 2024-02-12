package com.luoying.core;

import com.luoying.model.enums.QuestionSubmitLanguageEnum;
import com.luoying.template.CodeSandBoxTemplate;

/**
 * @author 落樱的悔恨
 * 代码沙箱工厂
 */
public class CodeSandboxFactory {
    /**
     * 根据编程语言获取对应的代码沙箱
     * @param language
     */
    public static CodeSandBoxTemplate getInstance(QuestionSubmitLanguageEnum language) {
         if (QuestionSubmitLanguageEnum.JAVA.equals(language)){// java
             return new JavaNativeCodeSandBox();
         } else if (QuestionSubmitLanguageEnum.CPLUSPLUS.equals(language)) {// c++
             return new CppNativeCodeSandBox();
         } else {
             throw new RuntimeException("暂不支持");
         }
    }
}