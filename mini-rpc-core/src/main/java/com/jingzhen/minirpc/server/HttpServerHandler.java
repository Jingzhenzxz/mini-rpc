package com.jingzhen.minirpc.server;

import com.jingzhen.minirpc.RpcApplication;
import com.jingzhen.minirpc.model.RpcRequest;
import com.jingzhen.minirpc.model.RpcResponse;
import com.jingzhen.minirpc.registry.LocalRegistry;
import com.jingzhen.minirpc.serializer.Serializer;
import com.jingzhen.minirpc.serializer.SerializerFactory;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * HTTP 请求处理器
 * 该类处理来自客户端的 HTTP 请求，并根据请求内容执行相应的 RPC 调用，
 * 然后将执行结果以 HTTP 响应的方式返回给客户端。
 */
public class HttpServerHandler implements Handler<HttpServerRequest> {

    /**
     * 处理 HTTP 请求的主要逻辑
     * 当 HTTP 请求到达时，首先获取请求体，并解析为 RpcRequest 对象，
     * 然后通过反射调用本地注册的服务，并最终返回 RpcResponse 作为 HTTP 响应。
     *
     * @param request 当前的 HTTP 请求对象
     */
    @Override
    public void handle(HttpServerRequest request) {
        // 获取序列化器实例，序列化器类型从 RPC 配置信息中获取
        final Serializer serializer = SerializerFactory.getInstance(RpcApplication.getRpcConfig().getSerializer());

        // 输出日志，记录接收到的请求信息
        System.out.println("Received request: " + request.method() + " " + request.uri());

        // 异步处理 HTTP 请求的请求体
        // `bodyHandler` 用于异步读取请求的主体内容，把请求体反序列化为RpcRequest对象
        Serializer finalSerializer = serializer;
        request.bodyHandler(body -> {
            byte[] bytes = body.getBytes();  // 获取请求体的字节内容
            RpcRequest rpcRequest = null;
            try {
                // 使用序列化器将字节内容反序列化为 RpcRequest 对象
                rpcRequest = finalSerializer.deserialize(bytes, RpcRequest.class);
            } catch (Exception e) {
                // 如果反序列化失败，打印异常栈信息
                e.printStackTrace();
            }

            // 构造一个 RpcResponse 对象，用于返回响应数据
            RpcResponse rpcResponse = new RpcResponse();

            // 如果反序列化出来的 rpcRequest 为 null，直接返回错误消息
            if (rpcRequest == null) {
                rpcResponse.setMessage("rpcRequest is null");
                // 调用 doResponse 方法发送响应
                doResponse(request, rpcResponse, finalSerializer);
                return;  // 返回，避免后续处理
            }

            try {
                // 根据 RpcRequest 中的服务名称查找本地注册表中的服务实现类
                Class<?> implClass = LocalRegistry.get(rpcRequest.getServiceName());

                // 使用反射获取方法信息，并根据 RpcRequest 中的方法名、参数类型来查找方法
                Method method = implClass.getMethod(rpcRequest.getMethodName(), rpcRequest.getParameterTypes());

                // 使用反射调用方法，传递参数并获取返回值
                Object result = method.invoke(implClass.newInstance(), rpcRequest.getArgs());

                // 封装方法执行结果到 rpcResponse 中
                rpcResponse.setData(result);  // 设置返回数据
                rpcResponse.setDataType(method.getReturnType());  // 设置返回数据的类型
                rpcResponse.setMessage("ok");  // 设置消息为成功
            } catch (Exception e) {
                // 处理调用过程中出现的异常，打印异常栈信息
                e.printStackTrace();
                rpcResponse.setMessage(e.getMessage());  // 设置异常消息
                rpcResponse.setException(e);  // 设置异常对象
            }

            // 最终调用 doResponse 发送响应数据
            doResponse(request, rpcResponse, finalSerializer);
        });
    }

    /**
     * 发送响应
     * 将 RpcResponse 对象通过 HTTP 响应发送给客户端。
     * 首先将 RpcResponse 对象序列化为字节数组，然后返回给客户端。
     *
     * @param request 当前的 HTTP 请求对象
     * @param rpcResponse 需要发送的 RPC 响应对象
     * @param serializer 序列化器，用于将 RpcResponse 对象序列化为字节数据
     */
    void doResponse(HttpServerRequest request, RpcResponse rpcResponse, Serializer serializer) {
        // 获取 HTTP 响应对象，并设置响应头为 "application/json" 类型
        HttpServerResponse httpServerResponse = request.response()
                .putHeader("content-type", "application/json");

        try {
            // 使用序列化器将 RpcResponse 对象序列化为字节数组
            byte[] serialized = serializer.serialize(rpcResponse);

            // 将序列化后的字节数组包装成 Buffer 对象，作为响应体发送给客户端
            httpServerResponse.end(Buffer.buffer(serialized));
        } catch (IOException e) {
            // 如果序列化或响应发送过程中发生错误，捕获异常并打印堆栈信息
            e.printStackTrace();
            // 发送空的响应
            httpServerResponse.end(Buffer.buffer());
        }
    }
}