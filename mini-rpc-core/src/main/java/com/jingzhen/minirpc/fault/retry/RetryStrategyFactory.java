package com.jingzhen.minirpc.fault.retry;

import com.jingzhen.minirpc.spi.SpiLoader;

/**
 * 重试策略工厂（用于获取重试器对象）
 * @author ZXZ
 */
public class RetryStrategyFactory {
    // 静态代码块在类被 JVM 加载到内存后（加载阶段之后），在类初始化阶段执行
    static {
        SpiLoader.load(RetryStrategy.class);
    }

    /**
     * 默认重试器
     */
    private static final RetryStrategy DEFAULT_RETRY_STRATEGY = new NoRetryStrategy();

    /**
     * 获取实例
     *
     * @param key
     * @return
     */
    public static RetryStrategy getInstance(String key) {
        return SpiLoader.getInstance(RetryStrategy.class, key);
    }
}
