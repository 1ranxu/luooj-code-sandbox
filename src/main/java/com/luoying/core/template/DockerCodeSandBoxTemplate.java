package com.luoying.core.template;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.luoying.core.CodeSandBox;
import com.luoying.model.*;
import com.luoying.utils.ProcessUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class DockerCodeSandBoxTemplate implements CodeSandBox {
    // 顶级目录（相对于当前项目）
    String globalCodeDirPath;

    // 二级目录（用于区分编程语言）（相对于当前项目）
    String prefix;

    // 代码文件名
    String globalCodeFileName;

    // jdk镜像
    String imageName;

    // 超时时间 ms
    private static final long TIMEOUT = 5000L;

    // 容器名称
    String containerName;

    protected DockerCodeSandBoxTemplate(String globalCodeDirPath, String prefix, String globalCodeFileName, String imageName, String containerName) {
        this.globalCodeDirPath = globalCodeDirPath;
        this.prefix = prefix;
        this.globalCodeFileName = globalCodeFileName;
        this.imageName = imageName;
        this.containerName = containerName;
    }

    /**
     * 每个实现类必须实现编译以及运行的cmd
     *
     * @param UUID         目录
     * @param userCodePath 用户代码文件的绝对路径
     * @return {@link CodeSandBoxCmd}
     */
    protected abstract CodeSandBoxCmd getCmd(String UUID, String userCodePath);


    /**
     * 执行代码
     *
     * @param executeCodeRequest {@link ExecuteCodeRequest}
     * @return {@link ExecuteCodeResponse}
     */
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        File userCodeFile = null;
        ExecuteCodeResponse executeCodeResponse = null;
        QuestionSubmitJudgeInfo judgeInfo = null;
        try {
            // 获取输入用例
            List<String> inputList = executeCodeRequest.getInputList();
            // // 获取用户代码
            String code = executeCodeRequest.getCode();

            // 1. 把用户的代码保存为文件
            userCodeFile = saveCodeFile(code);

            // 获取用户代码文件的绝对路径(项目目录/顶级目录/二级目录/UUID/文件名.后缀)
            String userCodePath = userCodeFile.getAbsolutePath();

            // 获取目录的UUID
            String UUID = userCodeFile.getParentFile().getName();

            // 获取编译命令和执行命令
            CodeSandBoxCmd sandBoxCmd = getCmd(UUID, userCodePath);
            String compileCmd = sandBoxCmd.getCompileCmd();
            String runCmd = sandBoxCmd.getRunCmd();

            // 2. 编译代码
            ExecuteMessage compileCodeFileExecuteMessage = compileCode(compileCmd);
            log.info(compileCodeFileExecuteMessage.toString());

            // 3. 执行代码，得到输出结果
            List<ExecuteMessage> executeMessageList = runCodeFile(inputList, runCmd, userCodeFile.getParentFile().getParentFile().getAbsolutePath());

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
     * @return {@link File}
     */
    public File saveCodeFile(String code) {
        // 获取工作目录（即项目目录）
        String userDir = System.getProperty("user.dir");
        // 构建 项目目录/顶级目录
        String topDirectory = userDir + File.separator + globalCodeDirPath;
        if (!FileUtil.exist(topDirectory)) {// 如果顶级目录不存在就创建
            FileUtil.mkdir(topDirectory);
        }
        // 构建 项目目录/顶级目录/二级目录/UUID (通过prefix区分不同语言)
        String userCodeParentPath = topDirectory + File.separator + prefix + File.separator + UUID.randomUUID();
        // 构建 项目目录/顶级目录/二级目录/UUID/文件名.后缀 (用户代码文件)
        String userCodePath = userCodeParentPath + File.separator + globalCodeFileName;
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
            // 获取编译结果
            ExecuteMessage executeMessage = ProcessUtil.runProcessAndGetMessage(compileProcess, "编译");
            // 返回编译结果
            return executeMessage;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 3. 执行代码文件，获得执行结果列表
     *
     * @param inputList 输入用例
     * @param runCmd    运行的cmd
     * @return List<ExecuteMessage>
     */
    public List<ExecuteMessage> runCodeFile(List<String> inputList, String runCmd, String secondaryPath) {
        // 1.拉取镜像
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        List<Image> images = dockerClient.listImagesCmd().exec();
        // 判断要拉取的镜像是否已经存在
        boolean isImageExists = images.stream().anyMatch(image -> Arrays.asList(image.getRepoTags()).contains(imageName));
        if (!isImageExists) {
            // 获取拉取镜像命令对象
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(imageName);
            // 回调函数
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    log.info("下载镜像状态：" + item.getStatus());
                    super.onNext(item);
                }
            };
            // 执行拉取镜像命令
            try {
                pullImageCmd
                        .exec(pullImageResultCallback)
                        .awaitCompletion();
            } catch (InterruptedException e) {
                log.error("拉取镜像异常");
            }
            log.info("下载完成");
        }

        // 2.创建容器
        // 判断容器是否存在
        List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();
        Optional<Container> containerOptional = containers.stream().filter(container -> Arrays.asList(container.getNames()).contains("/" + containerName)).findFirst();
        String containerId;
        if (containerOptional.isPresent()) {
            // 存在
            Container container = containerOptional.get();
            // 判断容器是否启动，未启动则手动启动
            if (!"running".equals(container.getState())) {
                dockerClient.startContainerCmd(container.getId()).exec();
            }
            // 记录容器id
            containerId = container.getId();
        } else {
            // 容器不存在，创建容器
            CreateContainerCmd containerCmd = dockerClient.createContainerCmd(imageName).withName(containerName);
            HostConfig hostConfig = new HostConfig();
            // 把 项目目录/顶级目录/二级目录 映射到 容器内的目录
            hostConfig.setBinds(new Bind(secondaryPath, new Volume("/app")));
            // 限制最大内存
            hostConfig.withMemory(100 * 1000 * 1000L);
            // 不让内存往硬盘写
            hostConfig.withMemorySwap(0L);
            hostConfig.withCpuCount(1L);
//        hostConfig.withSecurityOpts(Arrays.asList("seccomp=安全管理配置字符串"));
            // 创建容器
            CreateContainerResponse createContainerResponse = containerCmd
                    .withReadonlyRootfs(true)
                    .withNetworkDisabled(true)
                    .withHostConfig(hostConfig)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withTty(true)
                    .exec();
            log.info("创建容器响应=>{}", createContainerResponse.toString());
            // 记录容器id
            containerId = createContainerResponse.getId();
            // 3.启动容器
            StartContainerCmd startContainerCmd = dockerClient.startContainerCmd(containerId);
            startContainerCmd.exec();
        }

        // docker exec code_sandbox sh -c echo '1 3' | java -cp /app Main
        // 4.执行命令并获取结果
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String input : inputList) {
            // 为每个输入用例的执行计时
            StopWatch stopWatch = new StopWatch();
            input = "'" + input + "'";
            // 构造命令
            String[] command = {"sh", "-c", "echo " + input + " | " + runCmd};
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient
                    .execCreateCmd(containerId)
                    .withCmd(command)
                    .withAttachStderr(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .exec();

            log.info("创建执行命令：" + Arrays.toString(command));


            ExecuteMessage executeMessage = new ExecuteMessage();
            final String[] message = {null};
            final String[] errorMessage = {null};
            // 每个输入用例的执行时间
            long time = 0L;
            // 判断是否超时
            boolean[] isTimeOut = new boolean[]{true};
            String execId = execCreateCmdResponse.getId();
            // 执行命令时的回调函数
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback(System.out, System.err) {
                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)) {
                        errorMessage[0] = new String(frame.getPayload());
                        log.info("输出错误结果" + errorMessage[0]);
                    } else {
                        message[0] = new String(frame.getPayload());
                        message[0] = message[0].substring(0, message[0].length() - 1);
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
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
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
                        maxMemory[0] = Math.max(maxMemory[0], statistics.getMemoryStats().getUsage().longValue());
                    } else {
                        log.info("容器未运行");
                        try {
                            dockerClient.close();
                        } catch (IOException e) {
                            log.info("关闭dockerClient失败");
                        }
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
            statsCmd.exec(statisticsResultCallback);

            // 执行
            try {
                stopWatch.start();
                dockerClient
                        .execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion(TIMEOUT, TimeUnit.MILLISECONDS);
                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();
            } catch (Exception e) {
                log.info("程序执行异常");
                throw new RuntimeException(e);
            }
            // 封装执行结果
            executeMessage.setMessage(message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0] / 1024);
            executeMessageList.add(executeMessage);
        }
        dockerClient.stopContainerCmd(containerId).exec();
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
            // 获取错误信息
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
     * @param userCodeFile 用户文件
     * @return
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
