package com.jingzhen.minirpc.fault.retry;

import com.github.rholder.retry.*;
import com.jingzhen.minirpc.model.RpcResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 固定时间间隔 - 重试策略
 */
@Slf4j
public class FixedIntervalRetryStrategy implements RetryStrategy {

    /**
     * 执行重试逻辑
     *
     * @param callable 需要重试的任务，类型为 `Callable<RpcResponse>`，即返回 `RpcResponse` 的任务
     * @return 返回执行成功后的 `RpcResponse` 对象
     * @throws ExecutionException 如果执行任务时抛出异常
     * @throws RetryException     如果重试失败，最终抛出的异常
     */
    public RpcResponse doRetry(Callable<RpcResponse> callable) throws ExecutionException, RetryException {
        // 创建一个 Retryer 对象，用于执行重试逻辑
        Retryer<RpcResponse> retryer = RetryerBuilder.<RpcResponse>newBuilder()
                // 配置重试条件：如果抛出 Exception 类型的异常则进行重试
                .retryIfExceptionOfType(Exception.class)

                // 配置重试的等待策略：每次重试之间固定等待 3 秒
                .withWaitStrategy(WaitStrategies.fixedWait(3L, TimeUnit.SECONDS))

                // 配置停止策略：最多重试 3 次，超过后停止
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))

                // 配置重试监听器：每次重试时输出当前的重试次数
                .withRetryListener(new RetryListener() {
                    @Override
                    public <V> void onRetry(Attempt<V> attempt) {
                        // 输出当前的重试次数，`attempt.getAttemptNumber()` 返回当前重试的次数
                        log.info("重试次数 {}", attempt.getAttemptNumber());
                    }
                })

                // 构建并返回 Retryer 对象
                .build();

        // 执行带有重试逻辑的任务，返回重试后的结果
        return retryer.call(callable);
    }
}
