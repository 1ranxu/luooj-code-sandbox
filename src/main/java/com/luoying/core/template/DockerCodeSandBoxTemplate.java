package com.luoying.core.template;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.luoying.core.CodeSandBox;
import com.luoying.model.*;
import com.luoying.model.enums.JudgeInfoMessagenum;
import com.luoying.utils.ProcessUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author 落樱的悔恨
 * Docker 代码沙箱模板
 */
@Slf4j
public abstract class DockerCodeSandBoxTemplate implements CodeSandBox {
    // 顶级目录（相对于当前项目）
    String topDirPath;

    // 二级目录（用于区分编程语言）（相对于当前项目）
    String secDirPath;

    // 代码文件名
    String codeFileName;

    // 镜像名称
    String imageName;

    // 容器名称
    String containerName;

    // 超时时间 ms
    private static final long TIMEOUT = 5000L;

    // 字典树
    private static final WordTree WORD_TREE;

    static {
        WORD_TREE = new WordTree();
        WORD_TREE.addWords("Files", "exec");
    }


    protected DockerCodeSandBoxTemplate(String topDirPath, String secDirPath, String codeFileName, String imageName, String containerName) {
        this.topDirPath = topDirPath;
        this.secDirPath = secDirPath;
        this.codeFileName = codeFileName;
        this.imageName = imageName;
        this.containerName = containerName;
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

            // 获取用户代码文件的绝对路径(项目目录/顶级目录/二级目录/UUID/文件名.后缀)
            String userCodePath = userCodeFile.getAbsolutePath();

            // 获取用户代码文件父目录的名称
            String userCodeParentDirName = userCodeFile.getParentFile().getName();

            // 获取编译命令和执行命令
            CodeSandBoxCmd sandBoxCmd = getCmd(userCodeParentDirName, userCodePath);
            String compileCmd = sandBoxCmd.getCompileCmd();
            String runCmd = sandBoxCmd.getRunCmd();

            // 2. 编译代码
            ExecuteMessage compileCodeFileExecuteMessage = compileCode(compileCmd);
            log.info(compileCodeFileExecuteMessage.toString());
            if (StrUtil.isNotBlank(compileCodeFileExecuteMessage.getErrorMessage())) {
                return getErrorResponse(compileCodeFileExecuteMessage);
            }


            // 3. 执行代码，得到输出结果
            List<ExecuteMessage> executeMessageList = runCodeFile(inputList, runCmd, userCodeFile.getParentFile().getParentFile().getAbsolutePath());
            for (ExecuteMessage executeMessage : executeMessageList) {
                if (StrUtil.isNotBlank(executeMessage.getErrorMessage())) {
                    return getErrorResponse(executeMessage);
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

    private ExecuteCodeResponse getErrorResponse(ExecuteMessage executeMessage) {
        QuestionSubmitJudgeInfo judgeInfo = new QuestionSubmitJudgeInfo();
        judgeInfo.setMessage(executeMessage.getErrorMessage());
        judgeInfo.setTime(-1L);
        judgeInfo.setMemory(-1D);
        return ExecuteCodeResponse.builder()
                .outputList(null)
                .message(executeMessage.getErrorMessage())
                .status(3)
                .judgeInfo(judgeInfo).build();
    }

    private ExecuteCodeResponse getSaveCodeErrorResponse() {
        QuestionSubmitJudgeInfo judgeInfo = new QuestionSubmitJudgeInfo();
        judgeInfo.setMessage(JudgeInfoMessagenum.DANGEROUS_OPERATION.getValue());
        judgeInfo.setTime(-1L);
        judgeInfo.setMemory(-1D);
        return ExecuteCodeResponse.builder()
                .outputList(null)
                .message(JudgeInfoMessagenum.DANGEROUS_OPERATION.getValue())
                .status(3)
                .judgeInfo(judgeInfo).build();
    }


    /**
     * 1. 保存用户代码到文件中
     * 文件中的路径应为: tempCode/language/UUID/代码文件
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

        // 构建 项目目录/顶级目录/二级目录/UUID (通过secDirPath区分不同语言)
        String userCodeParentPath = topDir + File.separator + secDirPath + File.separator + UUID.randomUUID();

        // 构建 项目目录/顶级目录/二级目录/UUID/文件名.后缀 (用户代码文件路径)
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
    public List<ExecuteMessage> runCodeFile(List<String> inputList, String runCmd, String secDirPath) {
        // 1.拉取镜像
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        List<Image> images = dockerClient.listImagesCmd().exec();
        // 判断要拉取的镜像是否已经存在
        boolean isImageExists = images.stream().anyMatch(image -> Arrays.asList(image.getRepoTags()).contains(imageName));
        if (!isImageExists) {// 不存在
            // 获取拉取镜像命令
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(imageName);
            // 拉取镜像回调函数（用于查看拉取镜像的状态）
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    log.info("下载镜像状态：" + item.getStatus());
                    super.onNext(item);
                }
            };
            // 执行拉取镜像命令
            try {
                pullImageCmd.exec(pullImageResultCallback).awaitCompletion();
            } catch (InterruptedException e) {
                log.error("拉取镜像异常");
            }
            log.info("下载完成");
        }

        // 2.创建容器
        // 获取所有容器（包括未启动的）
        List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
        // 根据名称判断容器是否存在（容器的实际名称：/自定义名称）
        Optional<Container> containerOptional = containers.stream().filter(container -> Arrays.asList(container.getNames()).contains("/" + containerName)).findFirst();
        String containerId;
        if (containerOptional.isPresent()) {// 存在
            // 获取容器
            Container container = containerOptional.get();
            // 判断容器是否启动，未启动则手动启动
            if (!"running".equals(container.getState())) {
                dockerClient.startContainerCmd(container.getId()).exec();
            }
            // 记录容器id
            containerId = container.getId();
        } else {// 容器不存在
            // 构建创建容器命令
            CreateContainerCmd containerCmd = dockerClient.createContainerCmd(imageName).withName(containerName);
            HostConfig hostConfig = new HostConfig();
            // 把 项目目录/顶级目录/二级目录 挂载到 容器内的目录
            hostConfig.setBinds(new Bind(secDirPath, new Volume("/app")));
            // 限制最大内存 100M
            hostConfig.withMemory(100 * 1000 * 1000L);
            // 不让内存往硬盘写
            hostConfig.withMemorySwap(0L);
            // cpu核心1个
            hostConfig.withCpuCount(1L);
            // hostConfig.withSecurityOpts(Arrays.asList("seccomp=安全管理配置字符串"));
            // 创建容器
            CreateContainerResponse createContainerResponse = containerCmd.withReadonlyRootfs(true).withNetworkDisabled(true).withHostConfig(hostConfig).withAttachStdin(true).withAttachStdout(true).withAttachStderr(true).withTty(true).exec();
            log.info("创建容器响应=>{}", createContainerResponse.toString());
            // 记录容器id
            containerId = createContainerResponse.getId();
            // 3.启动容器
            StartContainerCmd startContainerCmd = dockerClient.startContainerCmd(containerId);
            startContainerCmd.exec();
        }

        // 例子：docker exec code_sandbox sh -c echo 'input' | runCmd
        // 4.执行命令并获取结果
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
                        errorMessage[0] = new String(frame.getPayload());
                        errorMessage[0] = errorMessage[0].substring(0, errorMessage[0].length() - 1);
                        log.info("输出错误结果" + errorMessage[0]);
                    } else {
                        message[0] = new String(frame.getPayload());
                        message[0] = message[0].substring(0, message[0].length() - 1);// 去掉最后的\n符
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

            // 获取占用的内存
            final double[] maxMemory = {0L};
            // 容器状态命令
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            // 实时获取容器内存的回调函数
            ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
                @Override
                public void onNext(Statistics statistics) {
                    // 使用 inspectContainerCmd 获取容器详细信息
                    InspectContainerResponse containerInfo = dockerClient.inspectContainerCmd(containerId).exec();
                    // 获取容器状态
                    String containerStatus = containerInfo.getState().getStatus();
                    // 检查容器是否正在运行
                    if ("running".equalsIgnoreCase(containerStatus)) {
                        log.info("内存占用：" + statistics.getMemoryStats().getUsage() / 1024);
                        maxMemory[0] = Math.max(maxMemory[0], statistics.getMemoryStats().getUsage().longValue() / 1024);
                    } else {
                        log.info("容器未运行");
                        try {
                            dockerClient.close();
                        } catch (IOException e) {
                            log.info("关闭dockerClient失败");
                        }
                        // 关闭内存统计命令
                        statsCmd.close();
                    }
                }

                @Override
                public void onStart(Closeable closeable) {

                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onComplete() {

                }

                @Override
                public void close() throws IOException {

                }
            });

            // 开始内存统计（Docker容器的守护线程会一直实时获取容器的内存）
            statsCmd.exec(statisticsResultCallback);

            // 执行命令
            try {
                // 开始计时
                stopWatch.start();
                // 执行命令
                dockerClient.execStartCmd(execId).exec(execStartResultCallback).awaitCompletion(TIMEOUT, TimeUnit.MILLISECONDS);
                // 结束计时
                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();
            } catch (Exception e) {
                log.info("程序执行异常");
                throw new RuntimeException(e);
            }
            // 封装单个用例的执行结果
            executeMessage.setMessage(message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0]);
            executeMessageList.add(executeMessage);
        }
        // 执行完所有输入用例后关闭容器
        dockerClient.stopContainerCmd(containerId).exec();
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
        double maxMemory = 0;

        for (ExecuteMessage executeMessage : executeMessageList) {
            // 获取错误信息
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)) {// 如果ExecuteMessage的属性ErrorMessage不为空。则执行中存在错误
                // 构造ExecuteCodeResponse
                executeCodeResponse.setOutputList(null);
                executeCodeResponse.setMessage(errorMessage);
                executeCodeResponse.setStatus(3);
                executeCodeResponse.setJudgeInfo(null);
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
