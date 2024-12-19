package com.jingzhen.minirpc;

import com.jingzhen.minirpc.config.RegistryConfig;
import com.jingzhen.minirpc.config.RpcConfig;
import com.jingzhen.minirpc.constant.RpcConstant;
import com.jingzhen.minirpc.registry.Registry;
import com.jingzhen.minirpc.registry.RegistryFactory;
import com.jingzhen.minirpc.utils.ConfigUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * RPC 框架应用
 * 相当于 holder，存放了项目全局用到的变量。双检锁单例模式实现
 */
@Slf4j  // 使用 Lombok 的日志注解，自动生成日志记录器
public class RpcApplication {

    // 声明一个静态的 volatile 变量用于存储 RpcConfig 配置对象
    private static volatile RpcConfig rpcConfig;

    /**
     * 框架初始化，支持传入自定义配置
     *
     * @param newRpcConfig 自定义的 RPC 配置对象
     */
    public static void init(RpcConfig newRpcConfig) {
        // 将传入的配置对象赋值给 rpcConfig
        rpcConfig = newRpcConfig;
        log.info("rpc init, config = {}", newRpcConfig.toString());  // 打印初始化日志

        // 从配置中获取注册中心配置，并初始化注册中心
        RegistryConfig registryConfig = rpcConfig.getRegistryConfig();  // 获取注册中心的配置
        Registry registry = RegistryFactory.getInstance(registryConfig.getRegistry());  // 根据配置获取对应的注册中心实例
        registry.init(registryConfig);  // 初始化注册中心
        log.info("registry init, config = {}", registryConfig);  // 打印注册中心初始化日志

        // 创建并注册 JVM 的 Shutdown Hook，在 JVM 退出时执行清理操作
        Runtime.getRuntime().addShutdownHook(new Thread(registry::destroy));  // 关闭时销毁注册中心
    }

    /**
     * 默认初始化方法，加载默认配置
     * 当没有传入自定义配置时，尝试加载默认的配置文件
     */
    public static void init() {
        RpcConfig newRpcConfig;
        try {
            // 从配置文件加载默认配置
            newRpcConfig = ConfigUtils.loadConfig(RpcConfig.class, RpcConstant.DEFAULT_CONFIG_PREFIX);
        } catch (Exception e) {
            // 配置加载失败，使用默认值
            newRpcConfig = new RpcConfig();  // 加载失败时，创建一个新的 RpcConfig 对象
        }
        // 调用初始化方法
        init(newRpcConfig);
    }

    /**
     * 获取配置（双检锁单例模式）
     * 该方法采用双重检查锁定（Double-Checked Locking）实现懒加载配置对象。
     * 只有在第一次获取配置时才会进行初始化
     *
     * @return 返回 RpcConfig 配置对象
     */
    public static RpcConfig getRpcConfig() {
        // 检查配置是否为空
        if (rpcConfig == null) {
            // 如果为空，进入同步块
            synchronized (RpcApplication.class) {
                // 再次检查配置是否为空，避免多个线程同时初始化
                if (rpcConfig == null) {
                    init();  // 配置为空时调用 init() 方法进行初始化
                }
            }
        }
        // 返回配置对象
        return rpcConfig;
    }
}