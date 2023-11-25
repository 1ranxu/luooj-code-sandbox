package com.luoying.template;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.luoying.core.CodeSandBox;
import com.luoying.model.*;
import com.luoying.utils.ProcessUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * 代码沙箱模板
 * 注意每个实现类必须自定义代码存放路径
 */
@Slf4j
public abstract class CodeSandBoxTemplate implements CodeSandBox {
    // 所有用户代码的根目录
    String globalCodeDirPath;
    // 对不同语言进行分类存储
    String prefix;
    // 代码文件名
    String globalCodeFileName;
    // 超时时间
    private static final long TIMEOUT = 5000L;

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


    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        File userCodeFile = null;
        ExecuteCodeResponse executeCodeResponse = null;
        try {
            List<String> inputList = executeCodeRequest.getInputList();
            String code = executeCodeRequest.getCode();
            // 1. 把用户的代码保存为文件
            userCodeFile = saveCodeToFile(code);

            String userCodePath = userCodeFile.getAbsolutePath();
            String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();

            CodeSandBoxCmd sandBoxCmd = getCmd(userCodeParentPath, userCodePath);
            String compileCmd = sandBoxCmd.getCompileCmd();
            String runCmd = sandBoxCmd.getRunCmd();
            // 2. 编译代码
            ExecuteMessage compileCodeFileExecuteMessage = compileCode(compileCmd);
            System.out.println(compileCodeFileExecuteMessage);

            // 3. 执行代码，得到输出结果
            List<ExecuteMessage> executeMessageList = runCode(inputList, runCmd);
            // 4. 收集整理输出结果
            executeCodeResponse = getOutputResponse(executeMessageList);
        } catch (Exception e) {
            throw new RuntimeException(e);
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
        // 所有用户代码的根目录
        String userDir = System.getProperty("user.dir");
        String globalCodePath = userDir + File.separator + globalCodeDirPath;
        if (!FileUtil.exist(globalCodePath)) {
            FileUtil.mkdir(globalCodePath);
        }
        // 存放用户代码的具体目录，通过prefix区分不同语言
        String userCodeParentPath = globalCodePath+ File.separator + prefix + File.separator + UUID.randomUUID();
        // 用户代码文件
        String userCodePath = userCodeParentPath + File.separator + globalCodeFileName;
        return FileUtil.writeString(code, userCodePath,
                StandardCharsets.UTF_8);
    }


    /**
     * 2. 编译代码
     *
     * @param compileCmd
     * @return
     */
    private ExecuteMessage compileCode(String compileCmd) {
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtil.runProcessAndGetMessage(compileProcess, "编译");
            if (executeMessage.getExitValue() != 0) {
                throw new RuntimeException("编译错误");
            }
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
        List<ExecuteMessage> executeMessageList = new LinkedList<>();
        for (String input : inputList) {
            Process runProcess;
            try {
                runProcess = Runtime.getRuntime().exec(runCmd);
                new Thread(() -> {
                    try {
                        Thread.sleep(TIMEOUT);
                        log.info("超时了，中断");
                        runProcess.destroy();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                ExecuteMessage executeMessage = ProcessUtil.handleProcessInteraction(runProcess, input, "运行");
                log.info("{}", executeMessage);
                executeMessageList.add(executeMessage);
            } catch (IOException e) {
                throw new RuntimeException("程序执行异常，" + e);
            }
        }
        return executeMessageList;
    }


    /**
     * 4. 获取输出结果
     *
     * @param executeMessageList
     * @return
     */
    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        // 一组输入用例中某个用例执行时长的最大值，便于判断是否超时
        long maxTime = 0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)) {
                executeCodeResponse.setMessage(errorMessage);
                // 执行中存在错误
                executeCodeResponse.setStatus(3);
                break;
            }
            outputList.add((executeMessage.getMessage()));
            Long time = executeMessage.getTime();
            if (time != null) {
                maxTime = Math.max(maxTime, time);
            }
        }
        executeCodeResponse.setOutputList(outputList);
        // 表示正常运行完成
        if (outputList.size() == executeMessageList.size()) {
            executeCodeResponse.setStatus(2);
        }
        QuestionSubmitJudgeInfo questionSubmitJudgeInfo = new QuestionSubmitJudgeInfo();
        questionSubmitJudgeInfo.setTime(maxTime);
        executeCodeResponse.setJudgeInfo(questionSubmitJudgeInfo);
        return executeCodeResponse;
    }

    /**
     * 5. 删除文件
     *
     * @param userCodeFile
     * @return
     */
    public boolean deleteFile(File userCodeFile) {
        if (userCodeFile.getParentFile() != null) {
            String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
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
