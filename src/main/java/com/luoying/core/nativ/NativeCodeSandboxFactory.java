package com.luoying.core.nativ;

import com.luoying.model.enums.QuestionSubmitLanguageEnum;
import com.luoying.core.template.NativeCodeSandBoxTemplate;

/**
 * @author 落樱的悔恨
 * 原生代码沙箱工厂
 */
public class NativeCodeSandboxFactory {
    /**
     * 根据编程语言获取对应的代码沙箱
     * @param language 编程语言
     */
    public static NativeCodeSandBoxTemplate getInstance(QuestionSubmitLanguageEnum language) {
         if (QuestionSubmitLanguageEnum.JAVA.equals(language)){// java
             return new JavaNativeCodeSandBox();
         } else if (QuestionSubmitLanguageEnum.CPLUSPLUS.equals(language)) {// cpp
             return new CppNativeCodeSandBox();
         } else {
             throw new RuntimeException("暂不支持");
         }
    }
}