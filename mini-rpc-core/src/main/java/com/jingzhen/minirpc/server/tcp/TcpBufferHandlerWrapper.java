package com.jingzhen.minirpc.server.tcp;

import com.jingzhen.minirpc.protocol.ProtocolConstant;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.parsetools.RecordParser;

/**
 * TCP 消息处理器包装
 * <p>
 * 本类使用装饰者模式对原有的 `Buffer` 处理能力进行增强，解决了半包和粘包问题。
 * 它通过一个 `RecordParser` 来处理传入的字节流 `Buffer`，并将完整的消息交给原有的 `bufferHandler` 处理。
 */
public class TcpBufferHandlerWrapper implements Handler<Buffer> {

    /**
     * 解析器，用于处理 TCP 数据包中的半包和粘包问题
     * 通过 `RecordParser` 解析消息头和消息体
     */
    private final RecordParser recordParser;

    /**
     * 构造器，初始化 `TcpBufferHandlerWrapper` 并设置解析器
     *
     * @param bufferHandler 原始的 buffer 处理器，用于处理解析后的完整消息
     */
    public TcpBufferHandlerWrapper(Handler<Buffer> bufferHandler) {
        // 初始化 RecordParser 解析器
        recordParser = initRecordParser(bufferHandler);
    }

    /**
     * 处理方法，接收一个 `Buffer`，并交由 `RecordParser` 解析
     *
     * @param buffer 接收到的 TCP 数据缓冲区
     */
    @Override
    public void handle(Buffer buffer) {
        // 将接收到的 buffer 交给 RecordParser 进行解析处理
        recordParser.handle(buffer);
    }

    /**
     * 初始化 RecordParser 解析器，设置解析规则
     *
     * @param bufferHandler 原始的 buffer 处理器，用于处理完整的消息
     * @return 初始化后的 `RecordParser` 解析器实例
     */
    private RecordParser initRecordParser(Handler<Buffer> bufferHandler) {
        // 创建一个 `RecordParser`，用于按固定长度读取消息头
        RecordParser parser = RecordParser.newFixed(ProtocolConstant.MESSAGE_HEADER_LENGTH);

        // 设置 RecordParser 输出的处理器，这个处理器负责接收并拼接完整的消息
        parser.setOutput(new Handler<Buffer>() {
            // 保存消息体的长度，初始化时为 -1
            int size = -1;
            // 用于拼接完整消息的缓存
            Buffer resultBuffer = Buffer.buffer();

            /**
             * 处理每个接收到的 Buffer，将其解析为完整的消息
             *
             * @param buffer 接收到的数据
             */
            @Override
            public void handle(Buffer buffer) {
                // 1. 如果 `size` 为 -1，表示需要读取消息头
                if (-1 == size) {
                    // 从消息头中读取消息体的长度
                    size = buffer.getInt(13); // 消息体长度存储在消息头的第 13 个字节处
                    // 切换到固定大小模式，消息体长度为 `size`
                    parser.fixedSizeMode(size);
                    // 将消息头数据写入到 `resultBuffer` 中
                    resultBuffer.appendBuffer(buffer);
                } else {
                    // 2. 如果 `size` 不为 -1，表示已经获取到消息体的长度，接下来读取消息体
                    // 将消息体数据写入到 `resultBuffer` 中
                    resultBuffer.appendBuffer(buffer);
                    // 如果拼接后的 `resultBuffer` 已经包含完整的消息，交给原始的 bufferHandler 处理
                    bufferHandler.handle(resultBuffer);
                    // 处理完一次消息后，重置解析器，准备解析下一条消息
                    parser.fixedSizeMode(ProtocolConstant.MESSAGE_HEADER_LENGTH); // 重置为消息头的固定长度
                    size = -1; // 重置消息体长度
                    resultBuffer = Buffer.buffer(); // 清空结果缓冲区
                }
            }
        });

        // 返回初始化后的 RecordParser 解析器
        return parser;
    }
}