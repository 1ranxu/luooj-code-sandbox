package com.luoying.init;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DockerClientBuilder;
import com.luoying.config.ThreadPoolExecutorConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.luoying.core.template.DockerCodeSandBoxTemplate.topDirPath;

/**
 * 容器初始化
 *
 * @Author 落樱的悔恨
 * @Date 2024/10/9 14:14
 */
@Configuration
@Slf4j
public class ContainerInit {
    private static final int containerNum = ThreadPoolExecutorConfig.coreThreads;

    private static final String imageName = "multi-language-image";

    private static final String containerName = "container-pool-thread-";
    // 构建 项目目录/顶级目录
    private static final String topDir = System.getProperty("user.dir") + File.separator + topDirPath;

    @PostConstruct
    public void init() throws Exception {
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        // 1.创建容器
        // 获取所有容器（包括未启动的）
        List<Container> containers = dockerClient.listContainersCmd().withShowAll(true).exec();

        for (int i = 1; i <= containerNum; i++) {
            int x = i;
            // 根据名称判断容器是否存在（容器的实际名称：/自定义名称）
            Optional<Container> containerOptional = containers.stream().filter(container -> Arrays.asList(container.getNames()).contains("/" + containerName + x)).findFirst();
            if (!containerOptional.isPresent()) {// 容器不存在
                // 构建创建容器命令
                CreateContainerCmd containerCmd = dockerClient.createContainerCmd(imageName).withName(containerName + x);
                HostConfig hostConfig = new HostConfig();
                // 把 项目目录/顶级目录/二级目录 挂载到 容器内的目录
                hostConfig.setBinds(new Bind(topDir, new Volume("/app")));
                // 限制最大内存 100M
                hostConfig.withMemory(128 * 1000 * 1000L);
                // 不让内存往硬盘写
                hostConfig.withMemorySwap(0L);
                // cpu核心1个
                hostConfig.withCpuCount(1L);
                // hostConfig.withSecurityOpts(Arrays.asList("seccomp=安全管理配置字符串"));
                // 创建容器
                CreateContainerResponse createContainerResponse = containerCmd.withReadonlyRootfs(true).withNetworkDisabled(true).withHostConfig(hostConfig).withAttachStdin(true).withAttachStdout(true).withAttachStderr(true).withTty(true).exec();
                log.info("创建容器=>{}", createContainerResponse.toString());
            }
        }

    }
}
