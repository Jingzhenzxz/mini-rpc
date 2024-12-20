package com.jingzhen.minirpc.springboot.starter.bootstrap;

import com.jingzhen.minirpc.RpcApplication;
import com.jingzhen.minirpc.config.RpcConfig;
import com.jingzhen.minirpc.server.tcp.VertxTcpServer;
import com.jingzhen.minirpc.springboot.starter.annotation.EnableRpc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Rpc 框架启动
 */
@Slf4j
public class RpcInitBootstrap implements ImportBeanDefinitionRegistrar {

    /**
     * Spring 初始化时执行，初始化 RPC 框架
     * 该方法会在 Spring 容器启动时执行，用于初始化 RPC 框架的相关配置。
     * 根据 EnableRpc 注解的属性，决定是否启动 RPC 服务端。
     *
     * @param importingClassMetadata 导入类的元数据，用于获取注解的属性值
     * @param registry               BeanDefinitionRegistry 用于向 Spring 容器注册 Bean 定义
     */
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        // 获取 @EnableRpc 注解中的 "needServer" 属性，判断是否需要启动 RPC 服务端
        boolean needServer = (boolean) importingClassMetadata.getAnnotationAttributes(EnableRpc.class.getName())
                .get("needServer");

        // 初始化 RPC 框架，进行必要的配置和注册中心初始化
        RpcApplication.init();

        // 获取全局的 RPC 配置信息
        final RpcConfig rpcConfig = RpcApplication.getRpcConfig();

        // 如果需要启动服务器，则创建并启动 VertxTcpServer
        if (needServer) {
            // 创建一个 VertxTcpServer 实例，处理 TCP 请求
            VertxTcpServer vertxTcpServer = new VertxTcpServer();
            // 启动TCP而不是HTTP服务器，监听配置文件中的服务器端口
            vertxTcpServer.doStart(rpcConfig.getServerPort());
        } else {
            // 如果不需要启动服务端，则输出日志提示
            log.info("不启动 server");
        }
    }
}

