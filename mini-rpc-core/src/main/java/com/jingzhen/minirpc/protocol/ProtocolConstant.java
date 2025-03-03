package com.jingzhen.minirpc.protocol;

/**
 * 协议常量
 * @author ZXZ
 */
public interface ProtocolConstant {

    /**
     * 消息头长度，默认是17
     */
    int MESSAGE_HEADER_LENGTH = 17;

    /**
     * 协议魔数，用于标识协议，方便解析，固定值，1个字节。
     */
    byte PROTOCOL_MAGIC = 0x1;

    /**
     * 协议版本号
     */
    byte PROTOCOL_VERSION = 0x1;
}
