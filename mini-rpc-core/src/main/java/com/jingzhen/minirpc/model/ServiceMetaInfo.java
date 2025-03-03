package com.jingzhen.minirpc.model;

import cn.hutool.core.util.StrUtil;
import com.jingzhen.minirpc.constant.RpcConstant;
import lombok.Data;

/**
 * 服务元信息（注册信息）
 * @author ZXZ
 */
@Data // Lombok 注解，自动生成 getter、setter、toString、equals 和 hashCode 方法
public class ServiceMetaInfo {

    /**
     * 服务名称
     * 用于唯一标识一个服务，通常在服务注册和发现中使用。
     */
    private String serviceName;

    /**
     * 服务版本号
     * 服务的版本信息，默认为 `RpcConstant.DEFAULT_SERVICE_VERSION`。
     * 在某些情况下，同一个服务可能会有多个版本共存，通过版本号区分不同版本的服务。
     */
    private String serviceVersion = RpcConstant.DEFAULT_SERVICE_VERSION;

    /**
     * 服务域名
     * 记录服务所在的主机名或 IP 地址。可以是具体的 IP 地址或域名。
     */
    private String serviceHost;

    /**
     * 服务端口号
     * 服务运行的端口号，用于与服务端通信。RpcProviderBootstrap类中把它设置为了RpcConfig中的端口号，即8121
     */
    private Integer servicePort;

    /**
     * 服务分组
     * 该字段暂时没有实现，未来可以根据需求将服务按分组进行分类，默认为 "default"。
     * 分组可以用来实现服务的版本管理、环境区分等。
     */
    private String serviceGroup = "default";

    /**
     * 获取服务的键名
     * 服务键名由 `serviceName` 和 `serviceVersion` 组成，格式为 `serviceName:serviceVersion`。
     * 键名用于唯一标识一个服务，通常用于服务注册中心的服务标识。
     *
     * @return 服务键名
     */
    public String getServiceKey() {
        // 后续可以扩展服务分组，若需要分组则返回格式为 `serviceName:serviceVersion:serviceGroup`
        return String.format("%s:%s", serviceName, serviceVersion);
    }

    /**
     * 获取服务注册节点的键名
     * 服务注册节点的键名由服务键名、服务的主机（`serviceHost`）和端口（`servicePort`）组成，格式为：
     * `serviceName:serviceVersion/serviceHost:servicePort`。
     * 该键名在服务注册中心用于唯一标识一个服务实例。
     *
     * @return 服务注册节点的键名
     */
    public String getServiceNodeKey() {
        return String.format("%s/%s:%s", getServiceKey(), serviceHost, servicePort);
    }

    /**
     * 获取完整的服务地址
     * 服务地址是 `http://serviceHost:servicePort` 格式的 URL。
     * 如果 `serviceHost` 中已包含 `http`，则直接返回 `serviceHost:servicePort`，否则默认使用 `http` 协议。
     *
     * @return 完整的服务地址
     */
    public String getServiceAddress() {
        // 如果 serviceHost 中不包含 "http" 则默认使用 http 协议
        if (!StrUtil.contains(serviceHost, "http")) {
            return String.format("http://%s:%s", serviceHost, servicePort);
        }
        return String.format("%s:%s", serviceHost, servicePort);
    }
}