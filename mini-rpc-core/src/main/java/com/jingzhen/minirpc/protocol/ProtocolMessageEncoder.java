package com.jingzhen.minirpc.protocol;

import com.jingzhen.minirpc.serializer.Serializer;
import com.jingzhen.minirpc.serializer.SerializerFactory;
import io.vertx.core.buffer.Buffer;

import java.io.IOException;

/**
 * 协议消息编码器
 * 该类负责将协议消息对象编码成二进制数据流（Buffer），以便通过网络传输。编码过程包括序列化消息头和消息体。
 * 客户端发送请求和服务端返回响应时要用到本类。
 */
public class ProtocolMessageEncoder {

    /**
     * 编码方法，负责将协议消息对象编码成二进制数据流（Buffer）
     *
     * @param protocolMessage 要编码的协议消息对象
     * @return 编码后的二进制数据流（Buffer）
     * @throws IOException 如果编码过程发生错误，抛出异常
     */
    public static Buffer encode(ProtocolMessage<?> protocolMessage) throws IOException {
        // 如果协议消息或消息头为空，则返回空的 Buffer
        if (protocolMessage == null || protocolMessage.getHeader() == null) {
            return Buffer.buffer();
        }

        // 获取协议消息的头部信息
        ProtocolMessage.Header header = protocolMessage.getHeader();

        // 创建一个新的 Buffer 用于存储编码后的数据
        Buffer buffer = Buffer.buffer();

        // 依次将消息头的各个字段编码为字节并写入 Buffer
        buffer.appendByte(header.getMagic());  // 写入魔数
        buffer.appendByte(header.getVersion());  // 写入协议版本
        buffer.appendByte(header.getSerializer());  // 写入序列化方式
        buffer.appendByte(header.getType());  // 写入消息类型
        buffer.appendByte(header.getStatus());  // 写入消息状态
        buffer.appendLong(header.getRequestId());  // 写入请求 ID

        // 获取序列化器，根据协议头中的序列化方式选择合适的序列化器
        ProtocolMessageSerializerEnum serializerEnum = ProtocolMessageSerializerEnum.getEnumByKey(header.getSerializer());
        if (serializerEnum == null) {
            throw new RuntimeException("序列化协议不存在");
        }

        // 获取对应的序列化器实例
        Serializer serializer = SerializerFactory.getInstance(serializerEnum.getValue());

        // 使用序列化器将消息体序列化为字节数组
        byte[] bodyBytes = serializer.serialize(protocolMessage.getBody());

        // 将消息体的长度和序列化后的字节数据写入 Buffer
        buffer.appendInt(bodyBytes.length);  // 写入消息体的字节长度
        buffer.appendBytes(bodyBytes);  // 写入序列化后的消息体数据

        // 返回编码后的 Buffer
        return buffer;
    }
}