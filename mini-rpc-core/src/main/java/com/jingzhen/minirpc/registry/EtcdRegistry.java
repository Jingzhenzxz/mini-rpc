package com.jingzhen.minirpc.registry;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ConcurrentHashSet;
import cn.hutool.cron.CronUtil;
import cn.hutool.cron.task.Task;
import cn.hutool.json.JSONUtil;
import com.jingzhen.minirpc.config.RegistryConfig;
import com.jingzhen.minirpc.model.ServiceMetaInfo;
import io.etcd.jetcd.*;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;
import io.etcd.jetcd.watch.WatchEvent;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Etcd 注册中心
 */
public class EtcdRegistry implements Registry {

    private Client client;

    private KV kvClient;

    /**
     * 本机注册的节点 key 集合（用于维护续期）
     * 在注册到ETCD、unregister、destroy或心跳时使用
     */
    private final Set<String> localRegisterNodeKeySet = new HashSet<>();

    /**
     * 注册中心服务缓存
     * 在服务发现和监听（如果是DELETE，则删除缓存）时使用
     */
    private final RegistryServiceCache registryServiceCache = new RegistryServiceCache();

    /**
     * 正在监听的 key 集合
     */
    private final Set<String> watchingKeySet = new ConcurrentHashSet<>();

    /**
     * 根节点
     */
    private static final String ETCD_ROOT_PATH = "/rpc/";

    /**
     * 初始化 Etcd 注册中心客户端
     *
     * @param registryConfig 注册中心配置，包含 Etcd 的连接地址和超时等参数
     */
    @Override
    public void init(RegistryConfig registryConfig) {
        // 使用注册中心配置中的地址和超时参数构建 Etcd 客户端
        client = Client.builder()
                .endpoints(registryConfig.getAddress())  // Etcd 集群地址
                .connectTimeout(Duration.ofMillis(registryConfig.getTimeout()))  // 连接超时
                .build();

        // 获取 Etcd 的 KV 客户端，用于执行键值操作
        kvClient = client.getKVClient();

        // 启动心跳机制，维持与 Etcd 的连接
        heartBeat();
    }

    /**
     * 服务注册方法
     * 将服务信息注册到 Etcd，并通过租约控制服务的有效期。
     *
     * @param serviceMetaInfo 要注册的服务元信息（包括服务名称、地址等）
     * @throws Exception 如果注册过程中发生异常，抛出异常
     */
    @Override
    public void register(ServiceMetaInfo serviceMetaInfo) throws Exception {
        // 获取 Etcd 的 Lease 客户端，Lease 用于服务的过期控制
        Lease leaseClient = client.getLeaseClient();

        // 创建一个 30 秒的租约，租约在到期后，注册的服务将自动过期
        long leaseId = leaseClient.grant(30).get().getID();

        // 设置要存储到 Etcd 中的键值对
        String registerKey = ETCD_ROOT_PATH + serviceMetaInfo.getServiceNodeKey();  // 使用服务节点的键作为 Etcd 中的注册键
        ByteSequence key = ByteSequence.from(registerKey, StandardCharsets.UTF_8);  // 键
        // 值是整个serviceMetaInfo，serviceMetaInfo中包含key。
        ByteSequence value = ByteSequence.from(JSONUtil.toJsonStr(serviceMetaInfo), StandardCharsets.UTF_8);  // 值，使用 JSON 格式存储服务元数据

        // 将键值对与租约关联起来，设置过期时间为租约的有效期
        PutOption putOption = PutOption.builder().withLeaseId(leaseId).build();  // 使用租约设置过期时间
        kvClient.put(key, value, putOption).get();  // 执行键值对的存储操作，并关联租约

        // 将注册的节点信息添加到本地缓存中，避免重复注册
        localRegisterNodeKeySet.add(registerKey);
    }

    @Override
    public void unRegister(ServiceMetaInfo serviceMetaInfo) {
        String registerKey = ETCD_ROOT_PATH + serviceMetaInfo.getServiceNodeKey();
        kvClient.delete(ByteSequence.from(registerKey, StandardCharsets.UTF_8));
        // 也要从本地缓存移除
        localRegisterNodeKeySet.remove(registerKey);
    }

    /**
     * 服务发现方法
     * 该方法通过从 Etcd 获取服务的元信息（如服务地址、端口等），实现服务的发现。
     * 如果服务信息已经缓存，则直接返回缓存中的服务信息；否则，查询 Etcd 获取服务信息并更新缓存。
     *
     * @param serviceKey 服务的唯一标识（如服务名称）
     * @return 服务的元信息列表
     * @throws RuntimeException 如果获取服务信息失败，抛出异常
     */
    @Override
    public List<ServiceMetaInfo> serviceDiscovery(String serviceKey) {
        // 优先从缓存获取服务元信息列表
        List<ServiceMetaInfo> cachedServiceMetaInfoList = registryServiceCache.readCache();
        if (cachedServiceMetaInfoList != null) {
            // 如果缓存中存在服务信息，直接返回缓存中的服务列表
            return cachedServiceMetaInfoList;
        }

        // 构建 Etcd 查询前缀，服务的 key 后必须以 '/' 结尾
        String searchPrefix = ETCD_ROOT_PATH + serviceKey + "/";

        try {
            // 使用 GetOption 的 isPrefix 参数进行前缀查询
            // 设置 isPrefix 为 true，表示查询以该前缀开始的所有键
            GetOption getOption = GetOption.builder().isPrefix(true).build();

            // 通过 Etcd KV 客户端进行前缀查询，查找所有匹配的服务信息
            List<KeyValue> keyValues = kvClient.get(
                            ByteSequence.from(searchPrefix, StandardCharsets.UTF_8),  // 查询的前缀
                            getOption)  // 查询选项，启用前缀搜索
                    .get()  // 获取查询结果
                    .getKvs();  // 获取所有匹配的键值对

            // 将查询结果转换为 ServiceMetaInfo 对象列表
            List<ServiceMetaInfo> serviceMetaInfoList = keyValues.stream()
                    .map(keyValue -> {
                        // 获取 Etcd 存储的服务节点键
                        String key = keyValue.getKey().toString(StandardCharsets.UTF_8);
                        // 监听该键的变化，以便进行服务变更的实时监控
                        watch(key);

                        // 获取 Etcd 存储的服务节点的值（服务元信息）
                        String value = keyValue.getValue().toString(StandardCharsets.UTF_8);
                        // 反序列化服务元信息
                        return JSONUtil.toBean(value, ServiceMetaInfo.class);
                    })
                    .collect(Collectors.toList());  // 收集为列表

            // 将获取到的服务信息写入缓存，以供下次查询时使用
            registryServiceCache.writeCache(serviceMetaInfoList);

            // 返回获取到的服务信息列表
            return serviceMetaInfoList;
        } catch (Exception e) {
            // 如果查询过程中发生异常，抛出运行时异常
            throw new RuntimeException("获取服务列表失败", e);
        }
    }

    /**
     * 服务心跳续约机制
     * 该方法通过定时任务每 10 秒检查一次本节点已注册的服务信息，并续签它们的租约。
     * 如果某个服务节点的租约已经过期，则跳过该节点。如果租约未过期，则重新注册（续签）服务信息。
     * 这确保了服务节点在 Etcd 中持续有效，并防止服务注册信息过期。
     */
    @Override
    public void heartBeat() {
        // 设置一个定时任务，每 10 秒执行一次心跳续签操作
        CronUtil.schedule("*/10 * * * * *", new Task() {
            @Override
            public void execute() {
                // 遍历当前节点注册的所有服务的 key，尝试续签服务节点的租约
                for (String key : localRegisterNodeKeySet) {
                    try {
                        // 通过 Etcd KV 客户端获取该服务节点的最新信息
                        List<KeyValue> keyValues = kvClient.get(ByteSequence.from(key, StandardCharsets.UTF_8))
                                .get()  // 获取 Etcd 中的键值对
                                .getKvs();  // 获取服务节点的所有键值对（这里只需要一个）

                        // 如果没有找到对应的键值对，说明该节点的租约已过期
                        // 该服务需要重新注册，跳过续签操作
                        if (CollUtil.isEmpty(keyValues)) {
                            continue;
                        }

                        // 如果该节点未过期，则获取该节点的服务信息并重新注册（即续签租约）
                        KeyValue keyValue = keyValues.get(0);
                        String value = keyValue.getValue().toString(StandardCharsets.UTF_8);
                        // 将存储的服务元信息反序列化为 ServiceMetaInfo 对象
                        ServiceMetaInfo serviceMetaInfo = JSONUtil.toBean(value, ServiceMetaInfo.class);

                        // 重新注册该服务（续签服务信息的租约）
                        register(serviceMetaInfo);
                    } catch (Exception e) {
                        // 如果续签过程发生异常，抛出 RuntimeException
                        throw new RuntimeException(key + "续签失败", e);
                    }
                }
            }
        });

        // 设置支持秒级别的定时任务，确保定时任务可以精确到秒
        CronUtil.setMatchSecond(true);

        // 启动定时任务调度
        CronUtil.start();
    }

    /**
     * 监听（消费端）
     * 监听服务节点的变化，处理节点的添加、删除等事件。这个方法用于启动监听并处理服务发现时的变化。
     *
     * @param serviceNodeKey 需要监听的服务节点的键
     */
    @Override
    public void watch(String serviceNodeKey) {
        // 获取Watch客户端对象，用于监听指定的key
        Watch watchClient = client.getWatchClient();

        // 如果当前的 serviceNodeKey 还没有被监听过，添加它到监听的集合中
        // 若监听集合中添加成功，则说明这是一个新的监听请求
        boolean newWatch = watchingKeySet.add(serviceNodeKey);

        // 如果是新的监听请求，则开启监听
        if (newWatch) {
            // 开始监听指定的服务节点（key）的变化
            watchClient.watch(ByteSequence.from(serviceNodeKey, StandardCharsets.UTF_8), response -> {
                // 遍历监听到的所有事件
                for (WatchEvent event : response.getEvents()) {
                    // 根据事件类型处理不同的情况
                    switch (event.getEventType()) {
                        // 当监听到 key 被删除时触发该事件
                        case DELETE:
                            // 清理注册服务缓存，通常是当服务节点下线或失效时需要清除缓存
                            registryServiceCache.clearCache();
                            break;
                        // 如果是 PUT 或其他类型的事件，可以根据需求进行相应的处理
                        case PUT:
                        default:
                            break;
                    }
                }
            });
        }
    }

    /**
     * 销毁操作（消费端下线）
     *
     * 处理节点下线操作，包括清理本地注册的服务节点，以及释放连接和资源。
     */
    @Override
    public void destroy() {
        // 打印当前节点下线的信息，方便调试和日志记录
        System.out.println("当前节点下线");

        // 遍历本节点下的所有注册的 key，将这些 key 从注册中心删除，表示节点下线
        for (String key : localRegisterNodeKeySet) {
            try {
                // 使用 kvClient 删除指定的 key，这会删除与该 key 关联的服务注册信息
                kvClient.delete(ByteSequence.from(key, StandardCharsets.UTF_8)).get();
            } catch (Exception e) {
                // 如果删除失败，抛出运行时异常，并打印失败的 key 信息
                throw new RuntimeException(key + "节点下线失败");
            }
        }

        // 释放客户端资源
        // 关闭 kvClient 连接，释放与 ETCD 服务的连接
        if (kvClient != null) {
            kvClient.close();
        }

        // 关闭整个客户端连接，释放所有资源
        if (client != null) {
            client.close();
        }
    }
}
