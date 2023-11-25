package com.luoying.utils;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.luoying.model.ExecuteMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.StopWatch;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 、
 * 进程工具类
 */
@Slf4j
public class ProcessUtil {
    /**
     * 执行进程，并记录信息
     *
     * @param process
     * @param opName
     * @return
     */
    public static ExecuteMessage runProcessAndGetMessage(Process process, String opName) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        try {
            StopWatch watch = new StopWatch();
            watch.start();
            int exitValue = process.waitFor();
            executeMessage.setExitValue(exitValue);
            if (exitValue == 0) {
                log.info(opName + "成功");

            } else {
                // 异常退出
                log.error(opName + "失败，错误码为: {}", exitValue);
                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                String errorOutputLine = "";
                List<String> errorOutputStrList = new ArrayList<>();
                while ((errorOutputLine = errorBufferedReader.readLine()) != null) {
                    errorOutputStrList.add(errorOutputLine);
                }
                log.error("错误输出为：{}", errorOutputStrList);
                // 设置错误信息
                executeMessage.setErrorMessage(StringUtils.join(errorOutputStrList, "\n"));
            }

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String outputLine = "";
            List<String> outputStrList = new ArrayList<>();
            while ((outputLine = bufferedReader.readLine()) != null) {
                outputStrList.add(outputLine);
            }
            if (CollectionUtil.isNotEmpty(outputStrList)) {
                log.info("正常输出：{}", outputStrList);
                // 设置正常信息
                executeMessage.setMessage(StringUtils.join(outputStrList, "\n"));
            }
            watch.stop();
            executeMessage.setTime(watch.getLastTaskTimeMillis());
        } catch (Exception e) {
            throw new RuntimeException(opName + "错误：" + e);
        }

        return executeMessage;
    }

    /**
     * 执行交互式进程，并记录信息
     *
     * @param runProcess
     * @param input
     * @param opName
     * @return
     */
    public static ExecuteMessage handleProcessInteraction(Process runProcess, String input, String opName) {
        OutputStream outputStream = runProcess.getOutputStream();
        try {
            // 模拟控制台输入数据
            outputStream.write((input + "\n").getBytes());
            outputStream.flush();
            outputStream.close();
            return runProcessAndGetMessage(runProcess, opName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                outputStream.close();
            } catch (IOException e) {
                log.error("关闭输入流失败");
            }
        }
    }
}
