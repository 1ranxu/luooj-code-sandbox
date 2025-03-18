package com.luoying.utils;

import cn.hutool.core.collection.CollectionUtil;
import com.luoying.model.ExecuteMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.StopWatch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.List;

/**
 * @author 落樱的悔恨
 * 进程工具类
 */
@Slf4j
public class ProcessUtil {
    /**
     * 执行进程，并记录信息
     *
     * @param process 进程
     * @param opName  操作名
     * @return {@link ExecuteMessage}
     */
    public static ExecuteMessage runProcessAndGetMessage(Process process, String opName) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        try {
            // 开始计时
            StopWatch watch = new StopWatch();
            watch.start();
            // 起始内存
            MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
            long start = memoryMXBean.getHeapMemoryUsage().getUsed() + memoryMXBean.getNonHeapMemoryUsage().getUsed();
            log.info("{}start {}", opName, start / 1024);
            // 执行进程
            int exitValue = process.waitFor();
            // 设置退出码
            executeMessage.setExitValue(exitValue);

            if (exitValue == 0) {// 0-正常
                log.info(opName + "成功");

            } else {// 其他-异常
                // 异常退出
                log.error(opName + "失败，错误码为: {}", exitValue);
                // 获取错误输入流(Linux)
                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                /*// 获取错误输入流(Windows)
                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), "GBK"));*/
                // 读取错误信息
                String errorOutputLine = "";
                List<String> errorOutputStrList = new ArrayList<>();
                while ((errorOutputLine = errorBufferedReader.readLine()) != null) {
                    errorOutputStrList.add(errorOutputLine);
                }
                log.error("错误输出为：{}", errorOutputStrList);
                // 设置错误信息
                executeMessage.setErrorMessage(StringUtils.join(errorOutputStrList, "\n"));
            }
            // 获取正常输入流
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            // 读取正常信息
            String outputLine = "";
            List<String> outputStrList = new ArrayList<>();
            while ((outputLine = bufferedReader.readLine()) != null) {
                outputStrList.add(outputLine);
            }
            if (CollectionUtil.isNotEmpty(outputStrList)) { // 设置正常信息
                log.info("正常输出：{}", outputStrList);
                executeMessage.setMessage(StringUtils.join(outputStrList, "\n"));
            }
            // 结束计时
            watch.stop();
            // 结束内存
            long end = memoryMXBean.getHeapMemoryUsage().getUsed() + memoryMXBean.getNonHeapMemoryUsage().getUsed();
            log.info("{}end {}", opName, end / 1024);
            // 设置时间
            executeMessage.setTime(watch.getLastTaskTimeMillis());
            // 设置内存
            executeMessage.setMemory( (end - start) / 1024);
        } catch (Exception e) {
            throw new RuntimeException(opName + "错误：" + e);
        }

        return executeMessage;
    }

    /**
     * 执行交互式进程，并记录信息
     *
     * @param runProcess 运行命令进程
     * @param input      输入用例
     * @param opName     操作名
     * @return {@link ExecuteMessage}
     */
    public static ExecuteMessage handleProcessInteraction(Process runProcess, String input, String opName) {
        // 获取运行命令进程的输出流
        OutputStream outputStream = runProcess.getOutputStream();
        try {
            // 模拟控制台输入数据
            outputStream.write((input + "\n").getBytes());
            // 清空缓冲区
            outputStream.flush();
            // 关闭输出流
            outputStream.close();
            // 调用runProcessAndGetMessagez执行进程
            return runProcessAndGetMessage(runProcess, opName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException e) {
                log.error("关闭输入流失败");
            }
        }
    }
}
