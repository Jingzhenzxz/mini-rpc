package com.jingzhen.minirpc.server.tcp;

import io.vertx.core.Vertx;
import io.vertx.core.net.NetServer;
import lombok.extern.slf4j.Slf4j;

/**
 * Vertx TCP 服务器
 * <p>
 * 该类用于启动一个基于 Vert.x 的 TCP 服务器。通过 Vert.x 提供的 `NetServer`，它能够监听指定端口，并处理来自客户端的连接请求。
 * 服务器接收到请求后，会通过 `TcpServerHandler` 进行处理。
 * @author ZXZ
 */
@Slf4j  // Lombok 注解，自动生成日志记录器
public class VertxTcpServer {

    /**
     * 启动 TCP 服务器
     *
     * @param port 服务器监听的端口号
     */
    public void doStart(int port) {
        // 创建 Vert.x 实例，Vert.x 是一个异步、事件驱动的应用框架
        Vertx vertx = Vertx.vertx();

        // 创建 TCP 服务器实例
        NetServer server = vertx.createNetServer();

        // 设置连接处理器，用于处理客户端连接的请求
        // 这里传入的是一个自定义的请求处理器 `TcpServerHandler`，它会处理所有的连接请求
        server.connectHandler(new TcpServerHandler());

        // 启动服务器并监听指定的端口
        server.listen(port, result -> {
            // 如果监听成功，打印启动信息
            if (result.succeeded()) {
                log.info("TCP server started on port " + port);
            } else {
                // 如果启动失败，打印失败原因
                log.error("Failed to start TCP server: " + result.cause());
            }
        });
    }
}