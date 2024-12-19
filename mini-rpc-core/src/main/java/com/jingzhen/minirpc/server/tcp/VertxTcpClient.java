package com.jingzhen.minirpc.server.tcp;

import cn.hutool.core.util.IdUtil;
import com.jingzhen.minirpc.RpcApplication;
import com.jingzhen.minirpc.model.RpcRequest;
import com.jingzhen.minirpc.model.RpcResponse;
import com.jingzhen.minirpc.model.ServiceMetaInfo;
import com.jingzhen.minirpc.protocol.*;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Vertx TCP 请求客户端
 * <p>
 * 该类用于发送 TCP 请求并接收响应，使用 Vert.x 提供的网络客户端（NetClient）与远程 TCP 服务进行通信。
 * 它构造一个请求消息，发送到指定的服务端口，并等待服务端的响应。
 */
public class VertxTcpClient {

    /**
     * 发送请求
     *
     * @param rpcRequest 请求消息对象，包含需要调用的服务信息、方法参数等
     * @param serviceMetaInfo 服务的元数据信息，包含服务的主机和端口等
     * @return RpcResponse 响应消息对象，包含服务调用的结果
     * @throws InterruptedException 中断异常
     * @throws ExecutionException 执行异常
     */
    public static RpcResponse doRequest(RpcRequest rpcRequest, ServiceMetaInfo serviceMetaInfo) throws InterruptedException, ExecutionException {
        // 创建 Vert.x 实例，启动客户端
        Vertx vertx = Vertx.vertx();
        NetClient netClient = vertx.createNetClient();  // 创建一个 NetClient 实例，用于与 TCP 服务器进行通信

        // 使用 CompletableFuture 来异步处理响应
        CompletableFuture<RpcResponse> responseFuture = new CompletableFuture<>();

        // 连接到指定的服务端口和主机
        netClient.connect(serviceMetaInfo.getServicePort(), serviceMetaInfo.getServiceHost(),
                result -> {
                    // 检查连接是否成功
                    if (!result.succeeded()) {
                        System.err.println("Failed to connect to TCP server");  // 如果连接失败，打印错误并返回
                        return;
                    }

                    // 获取连接成功后的 NetSocket 实例，用于与服务器通信
                    NetSocket socket = result.result();

                    // 构造协议消息，包含请求头和请求体
                    ProtocolMessage<RpcRequest> protocolMessage = new ProtocolMessage<>();
                    ProtocolMessage.Header header = new ProtocolMessage.Header();
                    header.setMagic(ProtocolConstant.PROTOCOL_MAGIC);  // 设置协议魔术字，用于标识协议
                    header.setVersion(ProtocolConstant.PROTOCOL_VERSION);  // 设置协议版本
                    header.setSerializer((byte) ProtocolMessageSerializerEnum.getEnumByValue(RpcApplication.getRpcConfig().getSerializer()).getKey());  // 设置序列化方式
                    header.setType((byte) ProtocolMessageTypeEnum.REQUEST.getKey());  // 设置消息类型为请求
                    header.setRequestId(IdUtil.getSnowflakeNextId());  // 生成唯一的请求 ID
                    protocolMessage.setHeader(header);
                    protocolMessage.setBody(rpcRequest);  // 设置请求体为传入的 rpcRequest

                    // 对协议消息进行编码，转化为字节流
                    try {
                        Buffer encodeBuffer = ProtocolMessageEncoder.encode(protocolMessage);
                        socket.write(encodeBuffer);  // 发送编码后的请求消息
                    } catch (IOException e) {
                        throw new RuntimeException("协议消息编码错误", e);  // 编码失败时抛出异常
                    }

                    // 设置接收响应的 Buffer 处理器
                    TcpBufferHandlerWrapper bufferHandlerWrapper = new TcpBufferHandlerWrapper(
                            buffer -> {
                                try {
                                    // 解码接收到的响应消息
                                    ProtocolMessage<RpcResponse> rpcResponseProtocolMessage =
                                            (ProtocolMessage<RpcResponse>) ProtocolMessageDecoder.decode(buffer);
                                    responseFuture.complete(rpcResponseProtocolMessage.getBody());  // 完成响应，返回 RpcResponse
                                } catch (IOException e) {
                                    throw new RuntimeException("协议消息解码错误", e);  // 解码失败时抛出异常
                                }
                            }
                    );
                    socket.handler(bufferHandlerWrapper);  // 将处理器添加到 socket，用于处理接收到的数据

                });

        // 等待响应，阻塞当前线程直到接收到响应
        RpcResponse rpcResponse = responseFuture.get();

        // 请求响应后关闭 NetClient 连接
        netClient.close();

        // 返回响应结果
        return rpcResponse;
    }
}