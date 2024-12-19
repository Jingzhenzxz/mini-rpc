package com.jingzhen.minirpc.proxy;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.jingzhen.minirpc.RpcApplication;
import com.jingzhen.minirpc.config.RpcConfig;
import com.jingzhen.minirpc.constant.RpcConstant;
import com.jingzhen.minirpc.fault.retry.RetryStrategy;
import com.jingzhen.minirpc.fault.retry.RetryStrategyFactory;
import com.jingzhen.minirpc.fault.tolerant.TolerantStrategy;
import com.jingzhen.minirpc.fault.tolerant.TolerantStrategyFactory;
import com.jingzhen.minirpc.loadbalancer.LoadBalancer;
import com.jingzhen.minirpc.loadbalancer.LoadBalancerFactory;
import com.jingzhen.minirpc.model.RpcRequest;
import com.jingzhen.minirpc.model.RpcResponse;
import com.jingzhen.minirpc.model.ServiceMetaInfo;
import com.jingzhen.minirpc.registry.Registry;
import com.jingzhen.minirpc.registry.RegistryFactory;
import com.jingzhen.minirpc.serializer.Serializer;
import com.jingzhen.minirpc.serializer.SerializerFactory;
import com.jingzhen.minirpc.server.tcp.VertxTcpClient;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务代理（JDK 动态代理）
 * 该类使用 JDK 动态代理实现对远程服务的调用代理，支持通过反射拦截方法调用，构造请求并发起远程调用。
 */
public class ServiceProxy implements InvocationHandler {

    /**
     * 调用代理的方法（拦截方法调用）
     *
     * @param proxy  代理对象
     * @param method 被调用的方法
     * @param args   方法参数
     * @return 调用结果
     * @throws Throwable 如果执行过程中出现异常，则抛出异常
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 1. 构造 RPC 请求对象
        // 获取要调用的方法所在类的全名（服务名称）
        String serviceName = method.getDeclaringClass().getName();

        // 创建 RPC 请求对象，包含服务名称、方法名、方法参数等信息
        RpcRequest rpcRequest = RpcRequest.builder()
                .serviceName(serviceName)  // 服务名称
                .methodName(method.getName())  // 方法名称
                .parameterTypes(method.getParameterTypes())  // 参数类型
                .args(args)  // 方法参数
                .build();

        // 2. 从注册中心获取服务提供者信息
        // 获取当前应用的 RPC 配置信息
        RpcConfig rpcConfig = RpcApplication.getRpcConfig();

        // 获取注册中心实例
        Registry registry = RegistryFactory.getInstance(rpcConfig.getRegistryConfig().getRegistry());

        // 创建服务元信息对象
        ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
        serviceMetaInfo.setServiceName(serviceName);  // 设置服务名称
        serviceMetaInfo.setServiceVersion(RpcConstant.DEFAULT_SERVICE_VERSION);  // 设置服务版本（默认为默认版本）

        // 从注册中心发现服务提供者，获取可用的服务地址列表
        List<ServiceMetaInfo> serviceMetaInfoList = registry.serviceDiscovery(serviceMetaInfo.getServiceKey());

        // 如果没有找到可用的服务提供者，抛出异常
        if (CollUtil.isEmpty(serviceMetaInfoList)) {
            throw new RuntimeException("暂无服务地址");
        }

        // 3. 负载均衡
        // 获取负载均衡器实例
        LoadBalancer loadBalancer = LoadBalancerFactory.getInstance(rpcConfig.getLoadBalancer());

        // 构建负载均衡需要的请求参数（此处以方法名称作为负载均衡的依据）
        Map<String, Object> requestParams = new HashMap<>();
        requestParams.put("methodName", rpcRequest.getMethodName());

        // 根据负载均衡策略选择合适的服务提供者
        ServiceMetaInfo selectedServiceMetaInfo = loadBalancer.select(requestParams, serviceMetaInfoList);

        // 4. 使用重试机制发送 RPC 请求
        RpcResponse rpcResponse;
        try {
            // 获取重试策略
            RetryStrategy retryStrategy = RetryStrategyFactory.getInstance(rpcConfig.getRetryStrategy());

            // 执行重试机制，发起远程 RPC 请求
            rpcResponse = retryStrategy.doRetry(() ->
                    VertxTcpClient.doRequest(rpcRequest, selectedServiceMetaInfo)  // 使用 Vertx TCP 客户端发送请求
            );
        } catch (Exception e) {
            // 5. 容错机制
            // 如果发生异常，执行容错策略
            TolerantStrategy tolerantStrategy = TolerantStrategyFactory.getInstance(rpcConfig.getTolerantStrategy());
            rpcResponse = tolerantStrategy.doTolerant(null, e);  // 容错处理
        }

        // 返回 RPC 响应的数据
        return rpcResponse.getData();
    }

    /**
     * 发送 HTTP 请求
     * 如果需要使用 HTTP 进行远程调用，可以使用该方法。
     *
     * @param selectedServiceMetaInfo 选择的服务元信息（服务提供者的地址等）
     * @param bodyBytes 请求的消息体字节数组
     * @return RPC 响应对象
     * @throws IOException 如果请求过程中发生 I/O 异常，抛出异常
     */
    private static RpcResponse doHttpRequest(ServiceMetaInfo selectedServiceMetaInfo, byte[] bodyBytes) throws IOException {
        // 获取序列化器（使用配置中的默认序列化方式）
        final Serializer serializer = SerializerFactory.getInstance(RpcApplication.getRpcConfig().getSerializer());

        // 发送 HTTP 请求
        try (HttpResponse httpResponse = HttpRequest.post(selectedServiceMetaInfo.getServiceAddress())  // 发送 POST 请求到服务地址
                .body(bodyBytes)  // 请求体为序列化后的字节数据
                .execute()) {  // 执行请求并获得响应
            byte[] result = httpResponse.bodyBytes();  // 获取响应字节数据

            // 反序列化响应数据为 RpcResponse 对象
            RpcResponse rpcResponse = serializer.deserialize(result, RpcResponse.class);
            return rpcResponse;  // 返回 RPC 响应
        }
    }
}
