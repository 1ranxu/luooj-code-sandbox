package com.luoying.template;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import com.luoying.core.CodeSandBox;
import com.luoying.model.*;
import com.luoying.model.enums.JudgeInfoMessagenum;
import com.luoying.utils.ProcessUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * @author 落樱的悔恨
 * 代码沙箱模板
 * 注意每个实现类必须自定义代码存放路径
 */
@Slf4j
public abstract class CodeSandBoxTemplate implements CodeSandBox {
    // 顶级目录（相对于当前项目）
    String globalCodeDirPath;

    // 二级目录（用于区分编程语言）（相对于当前项目）
    String prefix;

    // 代码文件名
    String globalCodeFileName;

    // 超时时间 ms
    private static final long TIMEOUT = 5000L;

    // 字典树
    private static final WordTree WORD_TREE;

    static {
        WORD_TREE = new WordTree();
        WORD_TREE.addWords("Files", "exec");
    }

    protected CodeSandBoxTemplate(String globalCodeDirPath, String prefix, String globalCodeFileName) {
        this.globalCodeDirPath = globalCodeDirPath;
        this.prefix = prefix;
        this.globalCodeFileName = globalCodeFileName;
    }


    /**
     * 每个实现类必须实现编译以及运行的cmd
     *
     * @param userCodeParentPath 用户代码父目录的绝对路径
     * @param userCodePath       用户代码文件的绝对路径
     * @return {@link CodeSandBoxCmd}
     */
    protected abstract CodeSandBoxCmd getCmd(String userCodeParentPath,
                                             String userCodePath);

    /**
     * 执行代码
     * @param executeCodeRequest {@link ExecuteCodeRequest}
     * @return {@link ExecuteCodeResponse}
     */
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        // 该计时器用于执行过程中出错时
        StopWatch watch = new StopWatch();
        watch.start();

        // 该内存管理对象用于执行过程中出错时
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        long start = memoryMXBean.getHeapMemoryUsage().getUsed() + memoryMXBean.getNonHeapMemoryUsage().getUsed();

        // 用户代码文件、执行代码响应、判题信息对象的初始化
        File userCodeFile = null;
        ExecuteCodeResponse executeCodeResponse = null;
        QuestionSubmitJudgeInfo judgeInfo = null;

        try {
            // 获取输入用例
            List<String> inputList = executeCodeRequest.getInputList();
            // 获取用户代码
            String code = executeCodeRequest.getCode();

            // 1. 把用户的代码保存为文件
            try {
                userCodeFile = saveCodeToFile(code);
            } catch (Exception e) {
                // 异常，结束计时
                watch.stop();
                // 获取出异常时的内存
                long end = memoryMXBean.getHeapMemoryUsage().getUsed() + memoryMXBean.getNonHeapMemoryUsage().getUsed();
                // 设置判题信息
                judgeInfo = new QuestionSubmitJudgeInfo();
                judgeInfo.setMessage(JudgeInfoMessagenum.DANGEROUS_OPERATION.getValue());
                judgeInfo.setTime(watch.getLastTaskTimeMillis());
                judgeInfo.setMemory((double) (end - start) / 1024);
                // 构造执行代码响应，然后返回
                return ExecuteCodeResponse.builder()
                        .outputList(null)
                        .message(JudgeInfoMessagenum.DANGEROUS_OPERATION.getValue())
                        .judgeInfo(judgeInfo)
                        .build();
            }

            // 获取用户代码文件的绝对路径(项目目录/顶级目录/二级目录/UUID/文件名.后缀)
            String userCodePath = userCodeFile.getAbsolutePath();

            // 获取用户代码父目录的绝对路径(项目目录/顶级目录/二级目录/UUID)
            String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();

            // 获取编译命令和执行命令
            CodeSandBoxCmd sandBoxCmd = getCmd(userCodeParentPath, userCodePath);
            String compileCmd = sandBoxCmd.getCompileCmd();
            String runCmd = sandBoxCmd.getRunCmd();

            // 2. 编译代码
            ExecuteMessage compileCodeFileExecuteMessage = compileCode(compileCmd);
            if (compileCodeFileExecuteMessage.getExitValue() != 0) {
                log.info("编译信息：{}", compileCodeFileExecuteMessage);
                // 异常，结束计时
                watch.stop();
                // 获取出异常时的内存
                long end = memoryMXBean.getHeapMemoryUsage().getUsed() + memoryMXBean.getNonHeapMemoryUsage().getUsed();
                // 设置判题信息
                judgeInfo = new QuestionSubmitJudgeInfo();
                judgeInfo.setMessage(JudgeInfoMessagenum.COMPILE_ERROR.getValue());
                judgeInfo.setTime(watch.getLastTaskTimeMillis());
                judgeInfo.setMemory((double) (end - start) / 1024);
                // 构造执行代码响应，然后返回
                return ExecuteCodeResponse.builder()
                        .outputList(null)
                        .message(compileCodeFileExecuteMessage.getErrorMessage())
                        .judgeInfo(judgeInfo)
                        .build();
            }

            // 3. 执行代码，得到输出结果
            List<ExecuteMessage> executeMessageList = runCode(inputList, runCmd);
            for (ExecuteMessage executeMessage : executeMessageList) {
                if (executeMessage.getExitValue() != 0) {
                    // 异常，结束计时
                    watch.stop();
                    // 获取出异常时的内存
                    long end = memoryMXBean.getHeapMemoryUsage().getUsed() + memoryMXBean.getNonHeapMemoryUsage().getUsed();
                    // 设置判题信息
                    judgeInfo = new QuestionSubmitJudgeInfo();
                    judgeInfo.setMessage(JudgeInfoMessagenum.RUNTIME_ERROR.getValue());
                    judgeInfo.setTime(watch.getLastTaskTimeMillis());
                    judgeInfo.setMemory((double) (end - start) / 1024);
                    // 构造执行代码响应，然后返回
                    return ExecuteCodeResponse.builder()
                            .message(executeMessage.getErrorMessage())
                            .judgeInfo(judgeInfo)
                            .build();
                }
            }
            // 4. 收集整理输出结果
            executeCodeResponse = getOutputResponse(executeMessageList);
        } catch (
                Exception e) {
            log.info(e.getMessage());
        } finally {
            // 5. 文件清理，释放空间
            boolean b = deleteFile(userCodeFile);
            if (!b) {
                log.error("deleteFile error userCodeFilePath={}", userCodeFile.getAbsolutePath());
            }
        }
        return executeCodeResponse;
    }


    /**
     * 1. 保存用户代码到文件中
     * 保存到文件中的格式应为: tempCode/language/UUID/代码文件
     *
     * @param code 代码
     * @return
     */
    private File saveCodeToFile(String code) {
        // 使用字典树查看用户代码是否有一些危险操作
        FoundWord foundWord = WORD_TREE.matchWord(code);
        if (foundWord != null) {
            throw new RuntimeException("危险操作");
        }
        // 获取工作目录（即项目目录）
        String userDir = System.getProperty("user.dir");
        // 构建 项目目录/顶级目录
        String globalCodePath = userDir + File.separator + globalCodeDirPath;
        if (!FileUtil.exist(globalCodePath)) {// 如果顶级目录不存在就创建
            FileUtil.mkdir(globalCodePath);
        }
        // 构建 项目目录/顶级目录/二级目录/UUID (通过prefix区分不同语言)
        String userCodeParentPath = globalCodePath + File.separator + prefix + File.separator + UUID.randomUUID();
        // 构建 项目目录/顶级目录/二级目录/UUID/文件名.后缀 (用户代码文件)
        String userCodePath = userCodeParentPath + File.separator + globalCodeFileName;
        // 保存
        return FileUtil.writeString(code, userCodePath,
                StandardCharsets.UTF_8);
    }


    /**
     * 2. 编译代码
     *
     * @param compileCmd 编译命令
     * @return {@link ExecuteMessage}
     */
    private ExecuteMessage compileCode(String compileCmd) {
        try {
            // 获取编译的Process
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            // 获取编译结果
            ExecuteMessage executeMessage = ProcessUtil.runProcessAndGetMessage(compileProcess, "编译");
            // 返回编译结果
            return executeMessage;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 3. 执行字节码文件，获得执行结果列表
     *
     * @param inputList 输入用例
     * @param runCmd    运行的cmd
     * @return List<ExecuteMessage>
     */
    private List<ExecuteMessage> runCode(List<String> inputList, String runCmd) {
        // 用于保存每个输入用例的执行信息
        List<ExecuteMessage> executeMessageList = new LinkedList<>();
        // 遍历inputList，执行每个输入用例
        for (String input : inputList) {
            Process runProcess;
            try {
                // 获取运行的Process
                runProcess = Runtime.getRuntime().exec(runCmd);
                // 另起一个线程
                new Thread(() -> {
                    try {
                        // 如果超时了就销毁运行的Process
                        Thread.sleep(TIMEOUT);
                        log.info("超时了，中断");
                        runProcess.destroy();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                // 获取运行结果
                ExecuteMessage executeMessage = ProcessUtil.handleProcessInteraction(runProcess, input, "运行");
                log.info("{}", executeMessage);
                // 保存该输入用例的运行结果
                executeMessageList.add(executeMessage);
            } catch (IOException e) {
                throw new RuntimeException("程序执行异常，" + e);
            }
        }
        // 返回
        return executeMessageList;
    }


    /**
     * 4. 获取输出结果
     *
     * @param executeMessageList {@link List<ExecuteMessage>}
     * @return {@link ExecuteCodeResponse}
     */
    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList) {
        // 执行代码响应
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        // 输出结果
        List<String> outputList = new ArrayList<>();
        // 所有输入用例中某个用例执行时长的最大值，可以用于判断是否超时
        long maxTime = 0;
        // 所有输入用例中某个用例消耗内存的最大值
        double maxMemory = 0;

        for (ExecuteMessage executeMessage : executeMessageList) {
            // ，就，然后
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)) {// 如果ExecuteMessage的属性ErrorMessage不为空
                // 构造ExecuteCodeResponse
                executeCodeResponse.setMessage(errorMessage);
                // 执行中存在错误
                executeCodeResponse.setStatus(3);
                // 跳出循环
                break;
            }
            // 把正常输出添加到outputList
            outputList.add((executeMessage.getMessage()));
            // 获取时间
            Long time = executeMessage.getTime();
            if (time != null) {
                maxTime = Math.max(maxTime, time);
            }
            // 获取内存
            Double memory = executeMessage.getMemory();
            if (memory != null) {
                maxMemory = Math.max(maxMemory, memory);
            }
        }
        // 设置输出结果
        executeCodeResponse.setOutputList(outputList);

        // 集合大小一样，表示正常运行完成
        if (outputList.size() == executeMessageList.size()) {
            executeCodeResponse.setStatus(2);
        }
        // 设置判题信息
        QuestionSubmitJudgeInfo questionSubmitJudgeInfo = new QuestionSubmitJudgeInfo();
        questionSubmitJudgeInfo.setTime(maxTime);
        questionSubmitJudgeInfo.setMemory(maxMemory);
        executeCodeResponse.setJudgeInfo(questionSubmitJudgeInfo);
        // 返回
        return executeCodeResponse;
    }

    /**
     * 5. 删除文件
     *
     * @param userCodeFile
     * @return
     */
    public boolean deleteFile(File userCodeFile) {
        if (userCodeFile == null) {// 为空说明文件不存在，返回true
            return true;
        }
        if (userCodeFile.getParentFile() != null) {// 不为空说明文件的父目录存在
            // 获取文件的父目录
            String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
            // 删除
            boolean del = FileUtil.del(userCodeParentPath);
            log.info("删除" + (del ? "成功" : "失败"));
            return del;
        }
        return true;
    }


    /**
     * 6. 错误处理，提升程序健壮性
     */
    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        executeCodeResponse.setStatus(3);
        executeCodeResponse.setJudgeInfo(new QuestionSubmitJudgeInfo());
        return executeCodeResponse;
    }
}
