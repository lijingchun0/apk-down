package com.ljc.apkdown.utils.ftpPool;


import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.pool2.BaseObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.util.ObjectUtils;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * FTP 客户端连接池
 *
 * @author
 */
@Slf4j
public class FtpClientPool extends BaseObjectPool<FTPClient> {
    private static final int DEFAULT_POOL_SIZE = 8;

    private final BlockingQueue<FTPClient> ftpBlockingQueue;
    private final FtpClientFactory ftpClientFactory;

    /**
     * 初始化连接池，需要注入一个工厂来提供FTPClient实例
     *
     * @param ftpClientFactory ftp工厂
     * @throws Exception
     */
    public FtpClientPool(FtpClientFactory ftpClientFactory) throws Exception {
        this(DEFAULT_POOL_SIZE, ftpClientFactory);
    }

    public FtpClientPool(int poolSize, FtpClientFactory factory) throws Exception {
        this.ftpClientFactory = factory;
        ftpBlockingQueue = new ArrayBlockingQueue<>(poolSize);
        initPool(poolSize);
    }

    /**
     * 初始化连接池，需要注入一个工厂来提供FTPClient实例
     *
     * @param maxPoolSize 最大连接数
     * @throws Exception
     */
    private void initPool(int maxPoolSize) throws Exception {
        for (int i = 0; i < maxPoolSize; i++) {
            // 往池中添加对象
            addObject();
        }
    }


    @Override
    public FTPClient borrowObject() throws Exception {
        FTPClient client = ftpBlockingQueue.take();
        if (ObjectUtils.isEmpty(client)) {
            client = ftpClientFactory.create();
            // 放入连接池
            returnObject(client);
            // 验证对象是否有效
        } else if (!ftpClientFactory.validateObject(ftpClientFactory.wrap(client))) {
            // 对无效的对象进行处理
            invalidateObject(client);
            // 创建新的对象
            client = ftpClientFactory.create();
            // 将新的对象放入连接池
            returnObject(client);
        }
        return client;
    }

    @Override
    public void returnObject(FTPClient client) throws Exception {
        try {
            if (client != null && !ftpBlockingQueue.offer(client, 3, TimeUnit.SECONDS)) {
                ftpClientFactory.destroyObject(ftpClientFactory.wrap(client));
            }
        } catch (InterruptedException e) {
            log.error("return ftp client interrupted ...{}", e);
        }
    }

    @Override
    public void invalidateObject(FTPClient client) throws Exception {
        try {
            client.logout();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            ftpBlockingQueue.remove(client);
        }
    }

    /**
     * 增加一个新的链接，超时失效
     */
    @Override
    public void addObject() throws Exception {
        // 插入对象到队列
        ftpBlockingQueue.offer(ftpClientFactory.create(), 3, TimeUnit.SECONDS);
    }

    /**
     * 关闭连接池
     */
    @Override
    public void close() {
        try {
            while (ftpBlockingQueue.iterator().hasNext()) {
                FTPClient client = ftpBlockingQueue.take();
                ftpClientFactory.destroyObject(ftpClientFactory.wrap(client));
            }
        } catch (Exception e) {
            log.error("close ftp client ftpBlockingQueue failed...{}", e);
        }
    }
}