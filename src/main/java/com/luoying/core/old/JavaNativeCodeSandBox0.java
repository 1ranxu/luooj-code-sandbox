package com.luoying.core.old;

import com.luoying.model.ExecuteCodeRequest;
import com.luoying.model.ExecuteCodeResponse;
import com.luoying.template.old.JavaCodeSandBoxTemplate;
import org.springframework.stereotype.Component;

/**
 * Java原生代码沙箱
 */
@Component
public class JavaNativeCodeSandBox0 extends JavaCodeSandBoxTemplate {
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }
}
