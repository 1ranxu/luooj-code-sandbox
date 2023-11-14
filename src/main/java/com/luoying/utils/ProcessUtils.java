package com.luoying.utils;

import cn.hutool.core.util.StrUtil;
import com.luoying.model.ExecuteMessage;
import org.springframework.util.StopWatch;

import java.io.*;

/**
 * 、
 * 进程工具类
 */
public class ProcessUtils {
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
                System.out.println(opName + "成功");
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder compileOutputStringBuilder = new StringBuilder();
                String compileOutputLine = "";
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    compileOutputStringBuilder.append(compileOutputLine).append("\n");
                }
                executeMessage.setMessage(compileOutputStringBuilder.toString());
            } else {
                // 异常退出
                System.out.println(opName + "失败，错误码：" + exitValue);
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder compileOutputStringBuilder = new StringBuilder();
                String compileOutputLine = "";
                while ((compileOutputLine = bufferedReader.readLine()) != null) {
                    compileOutputStringBuilder.append(compileOutputLine).append("\n");
                }
                executeMessage.setMessage(compileOutputStringBuilder.toString());

                BufferedReader errorBufferedReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                StringBuilder errorCompileOutputStringBuilder = new StringBuilder();
                String errorCompileOutputLine = "";
                while ((errorCompileOutputLine = errorBufferedReader.readLine()) != null) {
                    errorCompileOutputStringBuilder.append(errorCompileOutputLine).append("\n");
                }
                executeMessage.setErrorMessage(errorCompileOutputStringBuilder.toString());
            }
            watch.stop();
            executeMessage.setTime(watch.getLastTaskTimeMillis());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return executeMessage;
    }

    /**
     * 执行交互式进程，并记录信息
     *
     * @param process
     * @return
     */
    public static ExecuteMessage runInteractProcessAndGetMessage(Process process, String args) {
        ExecuteMessage executeMessage = new ExecuteMessage();
        try {
            // 使用 OutputStream 向程序终端发送参数
            OutputStream outputStream = process.getOutputStream();
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream));
            String[] split = args.split(" ");
            String jonin = StrUtil.join("\n", split) + "\n";
            bufferedWriter.write(jonin);
            // 相当于输了回车，输入结束
            bufferedWriter.flush();
            // 逐行获取进程的正常输出
            InputStream inputStream = process.getInputStream();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder compileOutputStringBuilder = new StringBuilder();
            String compileOutputLine = "";
            while ((compileOutputLine = bufferedReader.readLine()) != null) {
                compileOutputStringBuilder.append(compileOutputLine);
            }
            executeMessage.setMessage(compileOutputStringBuilder.toString());

            process.waitFor();
            // 关闭流释放资源
            bufferedReader.close();
            inputStream.close();
            bufferedWriter.close();
            outputStream.close();
            process.destroy();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return executeMessage;
    }
}
