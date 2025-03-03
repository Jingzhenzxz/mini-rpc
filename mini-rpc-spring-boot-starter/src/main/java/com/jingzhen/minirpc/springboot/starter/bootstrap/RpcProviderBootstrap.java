package com.jingzhen.minirpc.springboot.starter.bootstrap;

import com.jingzhen.minirpc.RpcApplication;
import com.jingzhen.minirpc.config.RegistryConfig;
import com.jingzhen.minirpc.config.RpcConfig;
import com.jingzhen.minirpc.model.ServiceMetaInfo;
import com.jingzhen.minirpc.registry.LocalRegistry;
import com.jingzhen.minirpc.registry.Registry;
import com.jingzhen.minirpc.registry.RegistryFactory;
import com.jingzhen.minirpc.springboot.starter.annotation.RpcService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Rpc 服务提供者启动
 * @author ZXZ
 */
@Slf4j
public class RpcProviderBootstrap implements BeanPostProcessor {

    /**
     * Bean 初始化后执行，注册服务
     * 该方法会在 Spring 容器完成 Bean 初始化之后调用，用于进行服务的注册。
     * 如果当前 Bean 被注解 @RpcService 标记，表示它是一个 RPC 服务，需要注册到注册中心。
     *
     * @param bean     当前的 Bean 实例
     * @param beanName Bean 的名称
     * @return 返回原始的 Bean 实例，保持 Spring 容器的原始逻辑
     * @throws BeansException 如果在执行过程中出现 Bean 初始化错误，抛出该异常
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 获取当前 Bean 的 Class 类型
        Class<?> beanClass = bean.getClass();

        // 检查当前 Bean 是否有 @RpcService 注解
        RpcService rpcService = beanClass.getAnnotation(RpcService.class);

        // 如果该 Bean 没有 @RpcService 注解，跳过处理
        if (rpcService != null) {
            // 需要注册服务

            // 1. 获取服务的基本信息
            // 从 RpcService 注解中获取服务接口类
            Class<?> interfaceClass = rpcService.interfaceClass();

            // 如果 interfaceClass 为 void.class，表示没有指定接口类，则使用 Bean 实现的第一个接口
            if (interfaceClass == void.class) {
                interfaceClass = beanClass.getInterfaces()[0];
            }

            // 获取服务的名称，即接口的全类名
            String serviceName = interfaceClass.getName();

            // 获取服务的版本
            String serviceVersion = rpcService.serviceVersion();

            // 2. 注册服务

            // 本地注册：将服务信息注册到本地注册表中
            LocalRegistry.register(serviceName, beanClass);

            // 获取全局的 RPC 配置信息
            final RpcConfig rpcConfig = RpcApplication.getRpcConfig();

            // 获取注册中心的配置信息
            RegistryConfig registryConfig = rpcConfig.getRegistryConfig();

            // 从注册中心工厂获取注册中心实例
            Registry registry = RegistryFactory.getInstance(registryConfig.getRegistry());

            // 创建服务元数据对象，包含服务的相关信息
            ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
            serviceMetaInfo.setServiceName(serviceName); // 设置服务名称
            serviceMetaInfo.setServiceVersion(serviceVersion); // 设置服务版本
            serviceMetaInfo.setServiceHost(rpcConfig.getServerHost()); // 设置服务主机
            serviceMetaInfo.setServicePort(rpcConfig.getServerPort()); // 设置服务端口

            try {
                // 将服务元数据注册到注册中心
                registry.register(serviceMetaInfo);
            } catch (Exception e) {
                // 如果注册失败，抛出运行时异常，并附带失败的服务名称
                throw new RuntimeException(serviceName + " 服务注册失败", e);
            }
        }

        // 返回原始的 Bean 实例，保持 Spring 容器的正常流程
        return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
    }
}
