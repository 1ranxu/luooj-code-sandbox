package com.luoying.controller;

import com.luoying.core.docker.DockerCodeSandboxFactory;
import com.luoying.core.template.DockerCodeSandBoxTemplate;
import com.luoying.model.ExecuteCodeRequest;
import com.luoying.model.ExecuteCodeResponse;
import com.luoying.model.enums.QuestionSubmitLanguageEnum;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author 落樱的悔恨
 * 执行代码
 */
@RestController
public class MainController {
    // 定义鉴权请求头和密钥
    private static final String AUTH_REQUEST_HEADER = "auth";
    private static final String AUTH_REQUEST_SECRET = "secretKey";

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;


    /**
     * 执行代码
     *
     * @param executeCodeRequest 执行代码请求
     * @param request            {@link HttpServletRequest}
     * @param response           {@link HttpServletResponse}
     * @return {@link ExecuteCodeResponse 执行代码响应}
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
        // 原生代码沙箱
        /*NativeCodeSandBoxTemplate codeSandBoxTemplate = NativeCodeSandboxFactory
                .getInstance(QuestionSubmitLanguageEnum.getEnumByValue(executeCodeRequest.getLanguage()));*/
        // Docker代码沙箱
        DockerCodeSandBoxTemplate codeSandBoxTemplate = DockerCodeSandboxFactory.getInstance(QuestionSubmitLanguageEnum.getEnumByValue(executeCodeRequest.getLanguage()));

        // 执行代码
        Callable<ExecuteCodeResponse> callable = () -> codeSandBoxTemplate.executeCode(executeCodeRequest);
        Future<ExecuteCodeResponse> future = threadPoolExecutor.submit(callable);
        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
