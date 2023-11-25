package com.luoying.core.old;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.luoying.model.ExecuteCodeRequest;
import com.luoying.model.ExecuteCodeResponse;
import com.luoying.model.ExecuteMessage;
import com.luoying.template.old.JavaCodeSandBoxTemplate;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Java原生代码沙箱
 */
@Component
public class JavaDockerCodeSandBox0 extends JavaCodeSandBoxTemplate {

    private static final long TIMEOUT = 5000L;

    private static final boolean FIRST_INIT = false;

    private static final String IMAGE_NAME = "openjdk:8-alpine";
    private static final String CONTAINER_NAME = "java_code_sandbox";
    
    

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }

    /**
     * * 床创建容器，执行代码，得到结果
     * * @param userCodeFile
     *
     * @param inputList
     * @return
     */
    @Override
    public List<ExecuteMessage> runCodeFile(File userCodeFile, List<String> inputList) {
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        // 1.拉取镜像
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        List<Image> images = dockerClient.listImagesCmd().exec();
        // 判断要拉取的镜像是否已经存在
        boolean isImageExists = images.stream().anyMatch(image -> Arrays.asList(image.getRepoTags()).contains(IMAGE_NAME));
        if (!isImageExists) {
            // 拉取jdk镜像
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(IMAGE_NAME);
            // 回调函数
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载镜像状态：" + item.getStatus());
                    super.onNext(item);
                }
            };
            // 执行拉取镜像命令
            try {
                pullImageCmd
                        .exec(pullImageResultCallback)
                        .awaitCompletion();
            } catch (InterruptedException e) {
                System.out.println("拉取镜像异常");
                throw new RuntimeException(e);
            }
            System.out.println("下载完成");
        }

        // 2.创建容器
        // 判断容器是否存在
        List<Container> containers = dockerClient.listContainersCmd().exec();
        Optional<Container> containerOptional = containers.stream().filter(container -> Arrays.asList(container.getNames()).contains(CONTAINER_NAME)).findFirst();
        String containerId;
        if (containerOptional.isPresent()){
            // 存在
            Container container =containerOptional.get();
            // 判断容器是否启动，未启动则手动启动
            if(!"running".equals(container.getState())){
                dockerClient.startContainerCmd(container.getId()).exec();
            }
            containerId = container.getId();
        }else {
            // 容器不存在，创建容器
            CreateContainerCmd containerCmd = dockerClient.createContainerCmd(IMAGE_NAME).withName(CONTAINER_NAME);
            HostConfig hostConfig = new HostConfig();
            // 把本地存放字节码文件的目录映射到容器的内的/app目录
            hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
            // 限制最大内存
            hostConfig.withMemory(100 * 1000 * 1000L);
            // 不让内存往硬盘写
            hostConfig.withMemorySwap(0L);
            hostConfig.withCpuCount(1L);
//        hostConfig.withSecurityOpts(Arrays.asList("seccomp=安全管理配置字符串"));
            CreateContainerResponse createContainerResponse = containerCmd
                    .withReadonlyRootfs(true)
                    .withNetworkDisabled(true)
                    .withHostConfig(hostConfig)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withTty(true)
                    .exec();
            System.out.println(createContainerResponse);
            containerId = createContainerResponse.getId();
            // 3.启动容器
            StartContainerCmd startContainerCmd = dockerClient.startContainerCmd(containerId);
            startContainerCmd.exec();
        }

        // docker exec focused_jackson java -cp /app Main 1 3
        // 4.执行命令并获取结果
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String input : inputList) {
            // 为每个输入用例的执行计时
            StopWatch stopWatch = new StopWatch();
            // 构造命令
            String[] split = input.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, split);
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient
                    .execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStderr(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .exec();
            System.out.println("创建执行命令：" + cmdArray);


            ExecuteMessage executeMessage = new ExecuteMessage();
            final String[] message = {null};
            final String[] errorMessage = {null};
            // 每个输入用例的执行时间
            long time = 0L;
            // 判断是否超时
            boolean[] isTimeOut = new boolean[]{true};
            String execId = execCreateCmdResponse.getId();
            // 执行命令时的回调函数
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)) {
                        errorMessage[0] = new String(frame.getPayload());
                        System.out.println("输出错误结果" + errorMessage[0]);
                    } else {
                        message[0] = new String(frame.getPayload());
                        System.out.println("输出结果" + message[0]);
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
            final long[] maxMemory = {0L};
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
                @Override
                public void onNext(Statistics statistics) {
                    System.out.println("内存占用：" + statistics.getMemoryStats().getUsage());
                    maxMemory[0] = Math.max(maxMemory[0], statistics.getMemoryStats().getUsage().longValue());
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
                // 关闭内存统计
                statsCmd.close();
            } catch (InterruptedException e) {
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }
            // 封装执行结果
            executeMessage.setMessage(message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0]);
            executeMessageList.add(executeMessage);
        }
        return executeMessageList;
    }

}
