package com.luoying.core;

import com.luoying.model.ExecuteCodeRequest;
import com.luoying.model.ExecuteCodeResponse;

/**
 * @author 落樱的悔恨
 * 代码沙箱
 */
public interface CodeSandBox {

    /**
     * 执行代码
     *
     * @param executeCodeRequest 执行代码请求
     * @return {@link ExecuteCodeResponse 执行代码响应}
     */
    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);
}
