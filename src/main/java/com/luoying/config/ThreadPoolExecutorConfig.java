package com.luoying.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
public class ThreadPoolExecutorConfig {
    /**
     * 核心线程数
     * 超线程
     */
    public static final int coreThreads = 2 * Runtime.getRuntime().availableProcessors();
    /**
     * 最大线程数
     */
    private static final int maxThreads = coreThreads;
    /**
     * 最大生存时间
     */
    private static final long keepAliveTime = 0L;
    /**
     * 时间单位
     */
    private static final TimeUnit unit = TimeUnit.SECONDS;

    /**
     * 任务对垒
     */
    private static final LinkedBlockingQueue queue = new LinkedBlockingQueue(1000);

    /**
     * 线程名称
     */
    private static final String threadName = "container-pool-thread-";

    /**
     * 线程工厂
     */
    private static final ThreadFactory threadFactory = new ThreadFactory() {
        private int count = 1;

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName(threadName + count);
            count++;
            return thread;
        }
    };

    /**
     * 拒绝策略
     */
    private static final RejectedExecutionHandler policy = new ThreadPoolExecutor.CallerRunsPolicy();


    @Bean()
    public ThreadPoolExecutor threadPoolExecutor() {
        return new ThreadPoolExecutor(coreThreads, maxThreads, keepAliveTime, unit, queue, threadFactory, policy);
    }
}