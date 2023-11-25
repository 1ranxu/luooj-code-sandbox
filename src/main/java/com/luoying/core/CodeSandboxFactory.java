package com.luoying.core;

import com.luoying.model.enums.QuestionSubmitLanguageEnum;
import com.luoying.template.CodeSandBoxTemplate;

public class CodeSandboxFactory {
    public static CodeSandBoxTemplate getInstance(QuestionSubmitLanguageEnum language) {
         if (QuestionSubmitLanguageEnum.JAVA.equals(language)){
             return new JavaNativeCodeSandBox();
         } else if (QuestionSubmitLanguageEnum.CPLUSPLUS.equals(language)) {
             return new CppNativeCodeSandBox();
         } else {
             throw new RuntimeException("暂不支持");
         }
    }
}