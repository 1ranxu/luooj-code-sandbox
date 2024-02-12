package com.luoying.controller;

import com.luoying.core.CodeSandboxFactory;
import com.luoying.model.ExecuteCodeRequest;
import com.luoying.model.ExecuteCodeResponse;
import com.luoying.model.enums.QuestionSubmitLanguageEnum;
import com.luoying.template.CodeSandBoxTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author 落樱的悔恨
 * 执行代码
 */
@RestController
public class MainController {
    // 定义鉴权请求头和密钥
    private static final String AUTH_REQUEST_HEADER = "auth";
    private static final String AUTH_REQUEST_SECRET = "secretKey";


    /**
     * 执行代码
     *
     * @param executeCodeRequest
     * @return
     */
    @PostMapping("/executeCode")
    ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest, HttpServletRequest request, HttpServletResponse response) {
        // todo 改造成API签名认证
        String authHeader = request.getHeader(AUTH_REQUEST_HEADER);
        if (!AUTH_REQUEST_SECRET.equals(authHeader)) {
            response.setStatus(403);
            return null;
        }
        // 判空
        if (executeCodeRequest == null) {
            throw new RuntimeException("请求参数为空");
        }
        // 根据编程语言获取对应的代码沙箱
        CodeSandBoxTemplate codeSandBoxTemplate = CodeSandboxFactory
                .getInstance(QuestionSubmitLanguageEnum.getEnumByValue(executeCodeRequest.getLanguage()));
        // 执行代码
        return codeSandBoxTemplate.executeCode(executeCodeRequest);
    }
}
