package com.jingzhen.minirpc.loadbalancer;

import com.jingzhen.minirpc.model.ServiceMetaInfo;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 一致性哈希负载均衡器
 * 该类实现了基于一致性哈希算法的负载均衡策略。
 * 一致性哈希是一种将服务节点映射到哈希环的算法，常用于分布式系统中的负载均衡。
 * 它通过将每个服务节点和虚拟节点映射到一个哈希环上，并根据请求的哈希值选择对应的服务节点。
 */
public class ConsistentHashLoadBalancer implements LoadBalancer {

    /**
     * 一致性哈希环，存放虚拟节点
     * 使用 `TreeMap` 以便可以通过哈希值的顺序快速选择最接近的虚拟节点。
     * key：虚拟节点的哈希值，value：对应的服务节点信息（`ServiceMetaInfo`）。
     */
    private final TreeMap<Integer, ServiceMetaInfo> virtualNodes = new TreeMap<>();

    /**
     * 虚拟节点数
     * 每个服务节点会生成多个虚拟节点，目的是为了减少哈希环上的“空隙”，
     * 从而让负载更加均匀分布，提高负载均衡的效果。
     */
    private static final int VIRTUAL_NODE_NUM = 100;

    /**
     * 从服务节点列表中选择一个服务节点来处理请求
     * 该方法根据一致性哈希算法，在多个服务节点中选择一个最合适的节点。
     *
     * @param requestParams 请求参数，通常是请求中的一些信息，用于计算哈希值
     * @param serviceMetaInfoList 服务节点列表，每个节点包含了服务的元数据
     * @return 选择的服务节点信息（`ServiceMetaInfo`），如果没有服务节点则返回 null
     */
    @Override
    public ServiceMetaInfo select(Map<String, Object> requestParams, List<ServiceMetaInfo> serviceMetaInfoList) {
        // 如果服务节点列表为空，直接返回 null
        if (serviceMetaInfoList.isEmpty()) {
            return null;
        }

        // 构建一致性哈希虚拟节点环
        // 遍历所有服务节点，并为每个服务节点创建多个虚拟节点
        for (ServiceMetaInfo serviceMetaInfo : serviceMetaInfoList) {
            // 为每个服务节点创建多个虚拟节点
            for (int i = 0; i < VIRTUAL_NODE_NUM; i++) {
                // 计算虚拟节点的哈希值，并将其加入虚拟节点环
                int hash = getHash(serviceMetaInfo.getServiceAddress() + "#" + i);
                // 不同的哈希值，同一个节点
                virtualNodes.put(hash, serviceMetaInfo);
            }
        }

        // 获取请求参数的哈希值，用于定位请求应该路由到哪个服务节点
        int hash = getHash(requestParams);

        // 查找最接近且大于等于请求哈希值的虚拟节点
        // `ceilingEntry` 方法返回大于或等于给定哈希值的最小哈希值节点
        Map.Entry<Integer, ServiceMetaInfo> entry = virtualNodes.ceilingEntry(hash);

        // 如果没有找到大于或等于请求哈希值的虚拟节点，则选择环首部的节点。重点！
        if (entry == null) {
            entry = virtualNodes.firstEntry();
        }

        // 返回找到的服务节点
        return entry.getValue();
    }

    /**
     * 自定义的哈希算法，可以根据实际需求进行修改
     * 在该方法中，使用 `hashCode()` 方法计算对象的哈希值，作为哈希值。
     *
     * @param key 用于计算哈希值的对象，可以是请求参数或服务节点地址
     * @return 计算得到的哈希值
     */
    private int getHash(Object key) {
        // 这里直接使用对象的 `hashCode()` 方法来计算哈希值
        // 当然可以根据需要使用其他的哈希算法，例如 MD5、SHA 等
        return key.hashCode();
    }
}
