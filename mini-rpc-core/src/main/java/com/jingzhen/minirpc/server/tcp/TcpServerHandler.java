package com.jingzhen.minirpc.server.tcp;

import com.jingzhen.minirpc.model.RpcRequest;
import com.jingzhen.minirpc.model.RpcResponse;
import com.jingzhen.minirpc.protocol.*;
import com.jingzhen.minirpc.registry.LocalRegistry;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * TCP 请求处理器。VertxTcpServer 中的 socket.connectHandler 会调用该类，用于处理接收到的 TCP 请求。
 * <p>
 * 该类负责处理接收到的 TCP 请求。具体来说，它接收一个 `NetSocket`（表示一个 TCP 连接），解析并处理该连接上的请求，执行相应的服务调用，
 * 最后将响应数据编码并发送回客户端。
 */
public class TcpServerHandler implements Handler<NetSocket> {

    /**
     * 处理请求
     *
     * @param socket 表示当前 TCP 连接的 NetSocket 实例
     *               该参数包含了接收到的数据、客户端的连接信息等
     */
    @Override
    public void handle(NetSocket socket) {
        // 创建 TcpBufferHandlerWrapper，用于处理接收到的 Buffer 数据
        TcpBufferHandlerWrapper bufferHandlerWrapper = new TcpBufferHandlerWrapper(buffer -> {
            // 解码请求数据为 ProtocolMessage 对象
            ProtocolMessage<RpcRequest> protocolMessage;
            try {
                // 使用 ProtocolMessageDecoder 解码接收到的 buffer 数据
                protocolMessage = (ProtocolMessage<RpcRequest>) ProtocolMessageDecoder.decode(buffer);
            } catch (IOException e) {
                // 解码失败，抛出异常
                throw new RuntimeException("协议消息解码错误");
            }

            // 获取协议消息的内容部分（RpcRequest）
            RpcRequest rpcRequest = protocolMessage.getBody();
            // 获取协议消息的头部信息（用于后续的响应）
            ProtocolMessage.Header header = protocolMessage.getHeader();

            // 创建 RpcResponse 对象，用于封装服务调用结果
            RpcResponse rpcResponse = new RpcResponse();
            try {
                // 获取要调用的服务实现类，从本地注册表中查找
                Class<?> implClass = LocalRegistry.get(rpcRequest.getServiceName());
                // 根据请求中提供的方法名称和参数类型获取方法对象
                Method method = implClass.getMethod(rpcRequest.getMethodName(), rpcRequest.getParameterTypes());
                // 使用反射调用目标方法
                Object result = method.invoke(implClass.newInstance(), rpcRequest.getArgs());
                // 将调用结果封装到 RpcResponse 中
                rpcResponse.setData(result);
                rpcResponse.setDataType(method.getReturnType());
                rpcResponse.setMessage("ok");
            } catch (Exception e) {
                // 调用失败，设置异常信息到响应对象中
                e.printStackTrace();
                rpcResponse.setMessage(e.getMessage());
                rpcResponse.setException(e);
            }

            // 设置响应消息的头部信息
            header.setType((byte) ProtocolMessageTypeEnum.RESPONSE.getKey());  // 设置消息类型为响应
            header.setStatus((byte) ProtocolMessageStatusEnum.OK.getValue());  // 设置消息状态为成功

            // 创建响应的协议消息
            ProtocolMessage<RpcResponse> responseProtocolMessage = new ProtocolMessage<>(header, rpcResponse);

            try {
                // 对响应协议消息进行编码
                Buffer encode = ProtocolMessageEncoder.encode(responseProtocolMessage);
                // 发送编码后的响应数据
                socket.write(encode);
            } catch (IOException e) {
                // 编码失败，抛出异常
                throw new RuntimeException("协议消息编码错误");
            }
        });

        // 将 bufferHandlerWrapper 设置为 NetSocket 的数据处理器
        socket.handler(bufferHandlerWrapper);
    }
}