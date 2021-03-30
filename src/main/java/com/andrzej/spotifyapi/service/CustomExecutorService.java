package com.andrzej.spotifyapi.service;

import java.util.concurrent.*;

public class CustomExecutorService extends ThreadPoolExecutor {

    public CustomExecutorService(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }
}
