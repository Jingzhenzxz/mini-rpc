package com.jingzhen.minirpc.protocol;

import com.jingzhen.minirpc.model.RpcRequest;
import com.jingzhen.minirpc.model.RpcResponse;
import com.jingzhen.minirpc.serializer.Serializer;
import com.jingzhen.minirpc.serializer.SerializerFactory;
import io.vertx.core.buffer.Buffer;

import java.io.IOException;

/**
 * 协议消息解码器
 * 该类负责解码从网络接收到的协议消息。解码过程包括解析消息头、验证魔数、解析消息体，
 * 根据不同的消息类型将消息体反序列化成相应的对象。
 * 客户端接受响应和服务端接受请求时要用到本类。
 * 从buffer中读取header和body，然后根据header中的serializer字段选择合适的序列化器进行反序列化。最后返回组装好的协议消息。
 */
public class ProtocolMessageDecoder {

    /**
     * 解码方法，负责将 Buffer 中的二进制数据解码成协议消息对象
     * Buffer是一段可变的内存缓冲区，通常用于存储二进制数据。主要用于处理字节数据流，且支持高效的操作和非阻塞的 I/O 操作。
     *
     * @param buffer 接收到的网络数据缓存
     * @return 解码后的协议消息对象
     * @throws IOException 如果解码过程出现错误，抛出异常
     */
    public static ProtocolMessage<?> decode(Buffer buffer) throws IOException {
        // 创建协议消息头对象，用于存储从 Buffer 中解析出的消息头信息
        ProtocolMessage.Header header = new ProtocolMessage.Header();

        // 读取消息的魔数 (magic)，魔数用于验证消息是否合法
        byte magic = buffer.getByte(0);

        // 校验魔数是否符合预期，若不符合则抛出异常
        if (magic != ProtocolConstant.PROTOCOL_MAGIC) {
            throw new RuntimeException("消息 magic 非法");
        }

        // 设置消息头的各项字段
        header.setMagic(magic);  // 设置魔数
        header.setVersion(buffer.getByte(1));  // 设置协议版本
        header.setSerializer(buffer.getByte(2));  // 设置序列化方式
        header.setType(buffer.getByte(3));  // 设置消息类型
        header.setStatus(buffer.getByte(4));  // 设置消息状态
        header.setRequestId(buffer.getLong(5));  // 设置请求 ID
        header.setBodyLength(buffer.getInt(13));  // 设置消息体长度

        // 解决粘包问题，确保只读取消息体部分的数据
        byte[] bodyBytes = buffer.getBytes(17, 17 + header.getBodyLength());

        // 根据消息头中的序列化方式，选择合适的序列化器进行消息体反序列化
        // 注意前面设置了header的serializer字段，所以这里不是采用的默认值！不要看漏了。
        ProtocolMessageSerializerEnum serializerEnum = ProtocolMessageSerializerEnum.getEnumByKey(header.getSerializer());
        if (serializerEnum == null) {
            throw new RuntimeException("序列化消息的协议不存在");
        }
        // 获取序列化器实例
        Serializer serializer = SerializerFactory.getInstance(serializerEnum.getValue());

        // 根据消息类型解析消息体
        // 注意前面设置了header的type字段，所以这里不是采用的默认值！不要看漏了。
        ProtocolMessageTypeEnum messageTypeEnum = ProtocolMessageTypeEnum.getEnumByKey(header.getType());
        if (messageTypeEnum == null) {
            throw new RuntimeException("序列化消息的类型不存在");
        }

        // 根据不同的消息类型，反序列化消息体并返回相应的协议消息对象
        switch (messageTypeEnum) {
            case REQUEST:
                // 反序列化请求消息体
                RpcRequest request = serializer.deserialize(bodyBytes, RpcRequest.class);
                // 返回封装好的请求消息对象
                return new ProtocolMessage<>(header, request);
            case RESPONSE:
                // 反序列化响应消息体
                RpcResponse response = serializer.deserialize(bodyBytes, RpcResponse.class);
                // 返回封装好的响应消息对象
                return new ProtocolMessage<>(header, response);
            case HEART_BEAT:
            case OTHERS:
            default:
                // 如果是心跳消息或其他不支持的类型，抛出异常
                throw new RuntimeException("暂不支持该消息类型");
        }
    }
}
