# 使用 Ubuntu 作为基础镜像
FROM ubuntu:latest
LABEL maintainer="1574925401@qq.com"

# 更新包列表并安装必要的包
RUN apt-get update && apt-get install -y \
    vim \
    openjdk-11-jdk \
    build-essential \
    golang-go \
    nodejs \
    rustc \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 设置环境变量
ENV JAVA_HOME /usr/lib/jvm/java-11-openjdk-amd64
ENV PATH $PATH:$JAVA_HOME/bin:/root/.cargo/bin:/usr/bin

# 设置工作目录
WORKDIR /app

# 使用 bin/sh 保持容器运行，提供交互式 shell
CMD ["/bin/sh"]