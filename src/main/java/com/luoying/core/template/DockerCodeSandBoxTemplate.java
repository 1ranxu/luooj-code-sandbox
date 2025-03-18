package com.luoying.core.template;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.luoying.core.CodeSandBox;
import com.luoying.exception.MemoryLimitExceededException;
import com.luoying.exception.TimeLimitExceededException;
import com.luoying.model.*;
import com.luoying.model.enums.JudgeInfoMessagenum;
import com.luoying.utils.ProcessUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.luoying.model.enums.JudgeInfoMessagenum.*;

/**
 * @author 落樱的悔恨
 * Docker 代码沙箱模板
 */
@Slf4j
public abstract class DockerCodeSandBoxTemplate implements CodeSandBox {
    // 顶级目录（相对于当前项目）
    public static final String topDirPath = "tempCode";

    // 代码文件名
    String codeFileName;

    // 最大超时时间 ms
    private static final long TIMEOUT = 2000L;

    // 最大内存 K
    private static final long MEMORYOUT = 120000L;

    // 字典树
    private static final WordTree WORD_TREE;

    static {
        WORD_TREE = new WordTree();
        WORD_TREE.addWords("Files", "exec");
    }


    protected DockerCodeSandBoxTemplate(String codeFileName) {
        this.codeFileName = codeFileName;
    }

    /**
     * 每个实现类必须实现（以提供编译以及运行的cmd）
     *
     * @param userCodeParentDirName 用户代码文件父目录的名称
     * @param userCodePath          用户代码文件的绝对路径
     * @return {@link CodeSandBoxCmd}
     */
    protected abstract CodeSandBoxCmd getCmd(String userCodeParentDirName, String userCodePath);

    /**
     * 执行代码
     *
     * @param executeCodeRequest 执行代码请求
     * @return {@link ExecuteCodeResponse 执行代码响应}
     */
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        File userCodeFile = null;
        ExecuteCodeResponse executeCodeResponse = null;
        try {
            // 获取输入用例
            List<String> inputList = executeCodeRequest.getInputList();

            // 获取用户代码
            String code = executeCodeRequest.getCode();

            // 1. 把用户的代码保存为文件
            try {
                userCodeFile = saveCodeFile(code);
            } catch (Exception e) {
                return getSaveCodeErrorResponse();
            }

            // 获取用户代码文件的绝对路径(项目目录/顶级目录/UUID/文件名.后缀)
            String userCodePath = userCodeFile.getAbsolutePath();

            // 获取用户代码文件父目录的名称
            String userCodeParentDirName = userCodeFile.getParentFile().getName();

            // 获取编译命令和执行命令
            CodeSandBoxCmd sandBoxCmd = getCmd(userCodeParentDirName, userCodePath);
            String compileCmd = sandBoxCmd.getCompileCmd();
            String runCmd = sandBoxCmd.getRunCmd();

            if (compileCmd != null) { // 有些语言不需要编译
                // 2. 编译代码
                ExecuteMessage compileCodeFileExecuteMessage = compileCode(compileCmd);
                if (StrUtil.isNotBlank(compileCodeFileExecuteMessage.getErrorMessage())) {
                    return getCompileCodeErrorResponse(compileCodeFileExecuteMessage);
                }
                log.info("编译信息:{}", compileCodeFileExecuteMessage);
            }

            // 3. 执行代码，得到输出结果
            List<ExecuteMessage> executeMessageList = null;
            try {
                executeMessageList = runCodeFile(inputList, runCmd, userCodeFile.getParentFile().getParentFile().getAbsolutePath());
            } catch (TimeLimitExceededException e) {
                return getTimeExceededErrorResponse(Long.valueOf(e.getMessage()));
            } catch (MemoryLimitExceededException e) {
                return getMemoryExceededErrorResponse((Long.valueOf(e.getMessage())));
            }
            for (ExecuteMessage executeMessage : executeMessageList) {
                log.info("运行信息:{}", executeMessage);
                if (StrUtil.isNotBlank(executeMessage.getErrorMessage())) {
                    return getRunCodeErrorResponse(executeMessage);
                }
            }

            // 4. 收集整理输出结果
            executeCodeResponse = getOutputResponse(executeMessageList);

        } catch (Exception e) {
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
     * 获取执行代码响应（保存代码失败）
     */
    private ExecuteCodeResponse getSaveCodeErrorResponse() {
        QuestionSubmitJudgeInfo judgeInfo = new QuestionSubmitJudgeInfo();
        judgeInfo.setMessage(JudgeInfoMessagenum.DANGEROUS_OPERATION.getValue());
        judgeInfo.setTime(-1L);
        judgeInfo.setMemory(-1L);
        return ExecuteCodeResponse.builder().outputList(null).message(JudgeInfoMessagenum.DANGEROUS_OPERATION.getValue()).status(3).judgeInfo(judgeInfo).build();
    }

    /**
     * 获取执行代码响应（编译代码失败）
     */
    private ExecuteCodeResponse getCompileCodeErrorResponse(ExecuteMessage executeMessage) {
        int index = executeMessage.getErrorMessage().indexOf(codeFileName, 0);
        String userCodeParentPath = executeMessage.getErrorMessage().substring(0, index);
        // 去除错误信息中的系统路径
        String errormessage = executeMessage.getErrorMessage().replace(userCodeParentPath, "");
        QuestionSubmitJudgeInfo judgeInfo = new QuestionSubmitJudgeInfo();
        judgeInfo.setMessage(errormessage);
        judgeInfo.setTime(-1L);
        judgeInfo.setMemory(-1L);
        return ExecuteCodeResponse.builder().outputList(null).message(COMPILE_ERROR.getValue()).status(3).judgeInfo(judgeInfo).build();
    }

    /**
     * 获取执行代码响应（运行代码失败）
     */
    private ExecuteCodeResponse getRunCodeErrorResponse(ExecuteMessage executeMessage) {
        QuestionSubmitJudgeInfo judgeInfo = new QuestionSubmitJudgeInfo();
        judgeInfo.setMessage(executeMessage.getErrorMessage());
        judgeInfo.setTime(-1L);
        judgeInfo.setMemory(-1L);
        return ExecuteCodeResponse.builder().outputList(null).message(RUNTIME_ERROR.getValue()).status(3).judgeInfo(judgeInfo).build();
    }

    /**
     * 获取执行代码响应（时间超限）
     */
    private ExecuteCodeResponse getTimeExceededErrorResponse(long time) {
        QuestionSubmitJudgeInfo judgeInfo = new QuestionSubmitJudgeInfo();
        judgeInfo.setMessage(TIME_LIMIT_EXCEEDED.getValue());
        judgeInfo.setTime(time);
        judgeInfo.setMemory(-1L);
        return ExecuteCodeResponse.builder().outputList(null).message(TIME_LIMIT_EXCEEDED.getValue()).status(3).judgeInfo(judgeInfo).build();
    }

    /**
     * 获取执行代码响应（内存超限）
     */
    private ExecuteCodeResponse getMemoryExceededErrorResponse(long memory) {
        QuestionSubmitJudgeInfo judgeInfo = new QuestionSubmitJudgeInfo();
        judgeInfo.setMessage(MEMORY_LIMIT_EXCEEDED.getValue());
        judgeInfo.setTime(-1L);
        judgeInfo.setMemory(memory);
        return ExecuteCodeResponse.builder().outputList(null).message(MEMORY_LIMIT_EXCEEDED.getValue()).status(3).judgeInfo(judgeInfo).build();
    }


    /**
     * 1. 保存用户代码到文件中
     * 文件中的路径应为: tempCode/UUID/代码文件
     *
     * @param code 代码
     * @return {@link File}
     */
    public File saveCodeFile(String code) {
        // 使用字典树查看用户代码是否有一些危险操作
        FoundWord foundWord = WORD_TREE.matchWord(code);
        if (foundWord != null) {
            throw new RuntimeException("危险操作");
        }

        // 获取工作目录（即项目目录）
        String userDir = System.getProperty("user.dir");

        // 构建 项目目录/顶级目录
        String topDir = userDir + File.separator + topDirPath;
        if (!FileUtil.exist(topDir)) {// 如果顶级目录不存在就创建
            FileUtil.mkdir(topDir);
        }

        // 构建 项目目录/顶级目录/UUID
        String userCodeParentPath = topDir + File.separator + UUID.randomUUID();

        // 构建 项目目录/顶级目录/UUID/文件名.后缀 (用户代码文件路径)
        String userCodePath = userCodeParentPath + File.separator + codeFileName;

        // 保存
        return FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
    }

    /**
     * 2. 编译代码
     *
     * @param compileCmd 编译命令
     * @return {@link ExecuteMessage}
     */
    public ExecuteMessage compileCode(String compileCmd) {
        try {
            // 获取编译的Process
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            // 返回编译结果
            return ProcessUtil.runProcessAndGetMessage(compileProcess, "编译");
        } catch (Exception e) {
            log.error("compile Exception:{}", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 3. 执行代码文件，获得执行结果列表
     *
     * @param inputList  输入用例
     * @param runCmd     运行的cmd
     * @param secDirPath 二级目录
     * @return {@link List<ExecuteMessage>}
     */
    public List<ExecuteMessage> runCodeFile(List<String> inputList, String runCmd, String secDirPath) throws InterruptedException, IOException {
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        // 1.启动容器
        // 获取所有容器（包括未启动的）
        List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
        // 根据线程名称判断容器是否存在（容器的实际名称：/自定义名称）
        Optional<Container> containerOptional = containers.stream().filter(container -> Arrays.asList(container.getNames()).contains("/" + Thread.currentThread().getName())).findFirst();
        String containerId;
        // 获取容器
        Container container = containerOptional.get();
        // 判断容器是否启动，未启动则手动启动
        if (!"running".equals(container.getState())) {
            dockerClient.startContainerCmd(container.getId()).exec();
        }
        // 记录容器id
        containerId = container.getId();

        CountDownLatch statsLatch = new CountDownLatch(1);
        // 获取占用的内存
        final long[] maxMemory = {0L};
        final long[] minMemory = {Long.MAX_VALUE};
        // 容器状态命令
        StatsCmd statsCmd = dockerClient.statsCmd(containerId);
        // 实时获取容器内存的回调函数
        ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
            @SneakyThrows
            @Override
            public void onNext(Statistics statistics) {
                // 使用 inspectContainerCmd 获取容器详细信息
                InspectContainerResponse containerInfo = dockerClient.inspectContainerCmd(containerId).exec();
                // 获取容器状态
                String containerStatus = containerInfo.getState().getStatus();
                if ("running".equalsIgnoreCase(containerStatus)) {
                    Long maxUsage = statistics.getMemoryStats().getMaxUsage();
                    log.info("内存占用：" + maxUsage / 1024);
                    maxMemory[0] = Math.max(maxMemory[0], maxUsage / 1024);
                    minMemory[0] = Math.min(minMemory[0], maxUsage / 1024);
                    log.info("maxUsage: {},  maxMemory: {},  minMemory: {}", maxUsage, maxMemory[0], minMemory[0]);
                } else {
                    log.info("容器状态:{}", containerStatus);
                }
            }

            @Override
            public void onStart(Closeable closeable) {

            }

            @Override
            public void onError(Throwable throwable) {
                log.info("内存统计异常:{}", throwable.getMessage());
            }

            @Override
            public void onComplete() {
                try {
                    // 进入阻塞队列挂起，等待主线程关闭容器后唤醒
                    statsLatch.await();
                    // 关闭内存统计命令
                    log.info("关闭内存统计");
                    statsCmd.close();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void close() throws IOException {

            }
        });

        // 开始内存统计（Docker容器的守护线程会一直实时获取容器的内存）
        statsCmd.exec(statisticsResultCallback);
        // 4.执行命令并获取结果
        // 例子：docker exec code_sandbox sh -c echo 'input' | runCmd
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String input : inputList) {
            // 为每个输入用例的执行计时
            StopWatch stopWatch = new StopWatch();
            input = "'" + input + "'";
            // 构造执行命令
            String[] command = {"sh", "-c", "echo " + input + " | " + runCmd};
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId).withCmd(command).withAttachStderr(true).withAttachStdin(true).withAttachStdout(true).exec();
            log.info("创建执行命令：" + Arrays.toString(command));
            // 执行信息
            ExecuteMessage executeMessage = new ExecuteMessage();
            final String[] message = {null};
            final String[] errorMessage = {null};
            // 每个输入用例的执行时间
            long time = 0L;
            // 判断是否超时
            boolean[] isTimeOut = new boolean[]{true};
            // 执行命令的id（用于定位我们要执行的命令）
            String execId = execCreateCmdResponse.getId();
            // 执行命令时的回调函数（明确我们在执行完命令后的操作）
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback(System.out, System.err) {
                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)) {
                        errorMessage[0] = new String(frame.getPayload(), StandardCharsets.UTF_8);
                        errorMessage[0] = errorMessage[0].substring(0, errorMessage[0].length() - 1);
                        log.info("输出错误结果" + errorMessage[0]);
                    } else {
                        message[0] = new String(frame.getPayload(), StandardCharsets.UTF_8);
                        if (message[0].charAt(message[0].length() - 1) == '\n') {
                            message[0] = message[0].substring(0, message[0].length() - 1);// 去掉最后的\n符
                        }
                        log.info("输出结果" + message[0]);
                    }
                    super.onNext(frame);
                }

                @Override
                public void onComplete() {
                    isTimeOut[0] = false;
                    super.onComplete();
                }
            };
            // 执行命令
            try {
                Thread.sleep(100);
                // 开始计时
                stopWatch.start();
                // 执行命令
                dockerClient.execStartCmd(execId).exec(execStartResultCallback).awaitCompletion(TIMEOUT, TimeUnit.MILLISECONDS);
                // 结束计时
                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();
                if (time >= TIMEOUT) {
                    log.info("超时{}", time);
                    throw new TimeLimitExceededException(TIME_LIMIT_EXCEEDED.getValue());
                }
                if (maxMemory[0] - minMemory[0] >= MEMORYOUT) {
                    log.info("溢出");
                    throw new MemoryLimitExceededException(MEMORY_LIMIT_EXCEEDED.getValue());
                }
            } catch (TimeLimitExceededException e) {
                log.info("程序执行超时");
                // 唤醒阻塞队列所有线程
                statsLatch.countDown();
                // 执行完所有输入用例后关闭容器
                dockerClient.stopContainerCmd(containerId).exec();
                // 关闭dockerClient
                dockerClient.close();
                throw new TimeLimitExceededException(String.valueOf(time));
            } catch (MemoryLimitExceededException e) {
                log.info("程序内存溢出");
                // 唤醒阻塞队列所有线程
                statsLatch.countDown();
                // 执行完所有输入用例后关闭容器
                dockerClient.stopContainerCmd(containerId).exec();
                // 关闭dockerClient
                dockerClient.close();
                throw new MemoryLimitExceededException(String.valueOf(maxMemory[0] - minMemory[0]));
            }

            // 封装单个用例的执行结果
            executeMessage.setMessage(message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setTime(time);
            log.info("单个用例的内存消耗:{}", maxMemory[0] - minMemory[0]);
            executeMessage.setMemory(maxMemory[0] - minMemory[0]);
            executeMessageList.add(executeMessage);
        }

        // 唤醒阻塞队列所有线程
        statsLatch.countDown();

        // 执行完所有输入用例后关闭容器
        dockerClient.stopContainerCmd(containerId).exec();

        // 关闭dockerClient
        try {
            dockerClient.close();
        } catch (IOException e) {
            log.info("dockerClient关闭失败");
        }

        // 返回
        return executeMessageList;
    }

    /**
     * 4. 获取输出结果
     *
     * @param executeMessageList 执行信息列表
     * @return {@link ExecuteCodeResponse 执行代码响应}
     */
    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList) {
        // 执行代码响应
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        // 输出结果
        List<String> outputList = new ArrayList<>();
        // 所有输入用例中某个用例执行时长的最大值，可以用于判断是否超时
        long maxTime = 0;
        // 所有输入用例中某个用例消耗内存的最大值
        long maxMemory = 0;

        for (ExecuteMessage executeMessage : executeMessageList) {
            // 把正常输出添加到outputList
            outputList.add((executeMessage.getMessage()));
            // 获取时间
            Long time = executeMessage.getTime();
            if (time != null) {
                maxTime = Math.max(maxTime, time);
            }
            // 获取内存
            Long memory = executeMessage.getMemory();
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
     * @param userCodeFile 用户文件
     */
    public boolean deleteFile(File userCodeFile) {
        if (userCodeFile == null) {// 为空说明文件不存在，返回true
            return true;
        }
        if (userCodeFile.getParentFile() != null) {// 不为空说明文件的父目录存在
            // 获取用户文件的父目录
            String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
            // 删除
            boolean del = FileUtil.del(userCodeParentPath);
            log.info("删除" + (del ? "成功" : "失败"));
            return del;
        }
        return true;
    }
}
