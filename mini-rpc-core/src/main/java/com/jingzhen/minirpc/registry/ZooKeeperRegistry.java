package com.jingzhen.minirpc.registry;

import cn.hutool.core.collection.ConcurrentHashSet;
import com.jingzhen.minirpc.config.RegistryConfig;
import com.jingzhen.minirpc.model.ServiceMetaInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.details.JsonInstanceSerializer;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * zookeeper 注册中心
 * 操作文档：<a href="https://curator.apache.org/docs/getting-started">Apache Curator</a>
 * 代码示例：<a href="https://github.com/apache/curator/blob/master/curator-examples/src/main/java/discovery/DiscoveryExample.java">DiscoveryExample.java</a>
 * 监听 key 示例：<a href="https://github.com/apache/curator/blob/master/curator-examples/src/main/java/cache/CuratorCacheExample.java">CuratorCacheExample.java</a>
 */
@Slf4j
public class ZooKeeperRegistry implements Registry {

    // CuratorFramework 用于与 ZooKeeper 进行交互的客户端实例
    private CuratorFramework client;

    // ServiceDiscovery 用于发现和获取服务元数据
    private ServiceDiscovery<ServiceMetaInfo> serviceDiscovery;

    /**
     * 本机注册的节点 key 集合（用于维护续期）
     * 在节点下线或续约时使用
     * - 用于存储当前节点已注册的服务的唯一标识（key），以便在服务下线或续约时使用。
     */
    private final Set<String> localRegisterNodeKeySet = new HashSet<>();

    /**
     * 注册中心服务缓存
     * - 用于缓存从注册中心（ZooKeeper）获取的服务信息，避免每次请求都查询 ZooKeeper。
     */
    private final RegistryServiceCache registryServiceCache = new RegistryServiceCache();

    /**
     * 正在监听的 key 集合
     * - 用于存储当前正在监听的服务节点的 `key`，用于监听服务状态变化（如服务新增、删除）。
     */
    private final Set<String> watchingKeySet = new ConcurrentHashSet<>();

    /**
     * 根节点路径
     * - 在 ZooKeeper 中，所有服务的元数据都以此路径为前缀进行存储。
     */
    private static final String ZK_ROOT_PATH = "/rpc/zk";


    /**
     * 初始化方法
     *
     * @param registryConfig 注册中心的配置
     * @throws RuntimeException 如果初始化过程中出现任何异常
     */
    @Override
    public void init(RegistryConfig registryConfig) {
        // 构建 ZooKeeper 客户端实例
        client = CuratorFrameworkFactory
                .builder()
                .connectString(registryConfig.getAddress())  // 设置 ZooKeeper 连接的地址
                .retryPolicy(new ExponentialBackoffRetry(Math.toIntExact(registryConfig.getTimeout()), 3)) // 设置重试策略
                .build();

        // 构建 ServiceDiscovery 实例，ServiceDiscovery 用于在 ZooKeeper 中发现服务
        serviceDiscovery = ServiceDiscoveryBuilder.builder(ServiceMetaInfo.class)  // 设置 ServiceMetaInfo 类型的服务元数据
                .client(client)  // 设置 ZooKeeper 客户端
                .basePath(ZK_ROOT_PATH)  // 设置服务注册的根路径
                .serializer(new JsonInstanceSerializer<>(ServiceMetaInfo.class))  // 设置服务元数据的序列化方式
                .build();

        try {
            // 启动 ZooKeeper 客户端和 ServiceDiscovery 实例
            client.start();
            serviceDiscovery.start();
        } catch (Exception e) {
            // 如果启动过程出现异常，抛出 RuntimeException
            throw new RuntimeException(e);
        }
    }

    @Override
    public void register(ServiceMetaInfo serviceMetaInfo) throws Exception {
        // 将服务信息注册到 ZooKeeper 中
        // `buildServiceInstance(serviceMetaInfo)` 根据传入的服务元数据构建服务实例，并通过 serviceDiscovery 注册到 ZooKeeper
        serviceDiscovery.registerService(buildServiceInstance(serviceMetaInfo));

        // 将服务的注册信息添加到本地缓存
        // 构建服务注册的唯一标识 key，格式为 "/rpc/zk/serviceNodeKey"
        String registerKey = ZK_ROOT_PATH + "/" + serviceMetaInfo.getServiceNodeKey();
        // 将注册节点的 key 存入本地缓存的 `localRegisterNodeKeySet` 中，用于后续的续约或注销
        localRegisterNodeKeySet.add(registerKey);
    }

    @Override
    public void unRegister(ServiceMetaInfo serviceMetaInfo) {
        try {
            // 注销服务，`buildServiceInstance(serviceMetaInfo)` 将服务信息转换为服务实例
            serviceDiscovery.unregisterService(buildServiceInstance(serviceMetaInfo));
        } catch (Exception e) {
            // 如果注销服务失败，则抛出运行时异常
            throw new RuntimeException(e);
        }

        // 从本地缓存中移除该服务的注册信息
        // 构建服务注册的唯一标识 key
        String registerKey = ZK_ROOT_PATH + "/" + serviceMetaInfo.getServiceNodeKey();
        // 将已注销的服务节点 key 从缓存中移除
        localRegisterNodeKeySet.remove(registerKey);
    }

    @Override
    public List<ServiceMetaInfo> serviceDiscovery(String serviceKey) {
        // 优先从本地缓存中读取服务信息
        List<ServiceMetaInfo> cachedServiceMetaInfoList = registryServiceCache.readCache();
        if (cachedServiceMetaInfoList != null) {
            // 如果缓存中有服务信息，直接返回缓存中的服务信息
            return cachedServiceMetaInfoList;
        }

        try {
            // 如果缓存中没有，从 ZooKeeper 查询服务实例
            // `serviceDiscovery.queryForInstances(serviceKey)` 会根据服务的唯一标识 `serviceKey` 查询相关的服务实例
            Collection<ServiceInstance<ServiceMetaInfo>> serviceInstanceList = serviceDiscovery.queryForInstances(serviceKey);

            // 解析查询到的服务实例，转换为 ServiceMetaInfo 类型的列表
            List<ServiceMetaInfo> serviceMetaInfoList = serviceInstanceList.stream()
                    .map(ServiceInstance::getPayload)  // 从 ServiceInstance 中获取服务元数据
                    .collect(Collectors.toList());

            // 将查询到的服务信息写入本地缓存，避免下次重复查询
            registryServiceCache.writeCache(serviceMetaInfoList);

            // 返回查询到的服务信息列表
            return serviceMetaInfoList;
        } catch (Exception e) {
            // 如果查询服务列表失败，则抛出运行时异常
            throw new RuntimeException("获取服务列表失败", e);
        }
    }

    @Override
    public void heartBeat() {
        // 不需要心跳机制
        // 使用临时节点的特性，Zookeeper 会自动管理临时节点的生命周期
        // 如果服务器发生故障或客户端与服务器断开连接，临时节点会自动丢失，因此不需要显式地发送心跳以维持连接。
    }

    @Override
    public void watch(String serviceNodeKey) {
        // 监听服务节点的变化
        // 构建完整的节点路径
        String watchKey = ZK_ROOT_PATH + "/" + serviceNodeKey;

        // 如果此节点还没有被监听过，则添加到 `watchingKeySet` 中
        // `watchingKeySet` 用来记录当前正在监听的节点
        boolean newWatch = watchingKeySet.add(watchKey);
        if (newWatch) {
            // 创建 CuratorCache 对象，用来监听指定路径的节点数据变化
            // `CuratorCache` 是一个专门用于监听 Zookeeper 节点数据变化的工具
            CuratorCache curatorCache = CuratorCache.build(client, watchKey);

            // 启动 CuratorCache，开始监听节点变化
            curatorCache.start();

            // 添加监听器，当节点发生变化时，执行相应的处理逻辑
            // 监听器会在以下几种情况发生时触发：
            // 1. 节点被删除（DELETE）
            // 2. 节点的数据发生变化（CHANGED）
            curatorCache.listenable().addListener(
                    CuratorCacheListener
                            .builder()
                            // 监听节点删除事件，触发时清空本地缓存
                            .forDeletes(childData -> registryServiceCache.clearCache())
                            // 监听节点数据变化事件，触发时清空本地缓存
                            .forChanges(((oldNode, node) -> registryServiceCache.clearCache()))
                            .build()
            );
        }
    }

    @Override
    public void destroy() {
        // 当节点下线时调用此方法
        log.info("当前节点下线");

        // 删除本节点注册的所有服务信息
        // 因为 Zookeeper 使用的是临时节点，节点自动失效后会自动被删除
        // 但是为了确保服务退出时能及时清理本地的所有注册信息，我们显式地删除注册节点
        for (String key : localRegisterNodeKeySet) {
            try {
                // 删除节点，保证删除操作成功
                client.delete().guaranteed().forPath(key);
            } catch (Exception e) {
                // 如果删除节点失败，抛出异常并给出具体信息
                throw new RuntimeException(key + "节点下线失败");
            }
        }

        // 释放资源，关闭 Curator 客户端连接
        if (client != null) {
            client.close();
        }
    }

    private ServiceInstance<ServiceMetaInfo> buildServiceInstance(ServiceMetaInfo serviceMetaInfo) {
        // 构建服务地址（格式为：服务主机:服务端口）
        String serviceAddress = serviceMetaInfo.getServiceHost() + ":" + serviceMetaInfo.getServicePort();

        try {
            // 创建并构建一个 ServiceInstance 对象
            // ServiceInstance 用于表示服务的一个实例，包含了服务的地址、ID、名称等信息
            return ServiceInstance
                    .<ServiceMetaInfo>builder() // 使用泛型指定服务负载类型为 ServiceMetaInfo
                    .id(serviceAddress) // 使用服务的地址作为服务实例的唯一标识（ID）
                    .name(serviceMetaInfo.getServiceKey()) // 设置服务的名称（使用服务的键作为名称）
                    .address(serviceAddress) // 设置服务的实际网络地址（即服务的主机名和端口）
                    .payload(serviceMetaInfo) // 设置服务实例的附加数据（即服务的元信息）
                    .build(); // 构建 ServiceInstance 实例并返回
        } catch (Exception e) {
            // 如果创建 ServiceInstance 失败，捕获异常并抛出 RuntimeException
            throw new RuntimeException(e);
        }
    }
}
