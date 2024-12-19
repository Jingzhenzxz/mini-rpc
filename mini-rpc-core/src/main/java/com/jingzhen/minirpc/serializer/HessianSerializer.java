package com.jingzhen.minirpc.serializer;

import com.caucho.hessian.io.HessianInput;
import com.caucho.hessian.io.HessianOutput;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Hessian 序列化器
 * <p>
 * Hessian 是一种高效的二进制序列化格式，适用于 Java 对象的序列化与反序列化。
 * 本类实现了 `Serializer` 接口，提供了基于 Hessian 协议的对象序列化和反序列化功能。
 */
public class HessianSerializer implements Serializer {

    /**
     * 序列化方法，将对象转换为字节数组
     *
     * @param object 要序列化的对象
     * @param <T>    对象的类型
     * @return 序列化后的字节数组
     * @throws IOException 如果序列化过程中发生 I/O 异常
     */
    @Override
    public <T> byte[] serialize(T object) throws IOException {
        // 创建一个 ByteArrayOutputStream 用于输出序列化后的字节数据
        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        // 创建 HessianOutput 实例，使用 ByteArrayOutputStream 作为底层流
        HessianOutput ho = new HessianOutput(bos);

        // 使用 HessianOutput 的 writeObject 方法将对象序列化到字节流中
        ho.writeObject(object);

        // 返回序列化后的字节数组
        return bos.toByteArray();
    }

    /**
     * 反序列化方法，将字节数组恢复为对象
     *
     * @param bytes  序列化后的字节数组
     * @param tClass 目标对象的类类型
     * @param <T>    对象的类型
     * @return 反序列化后的对象
     * @throws IOException 如果反序列化过程中发生 I/O 异常
     */
    @Override
    public <T> T deserialize(byte[] bytes, Class<T> tClass) throws IOException {
        // 创建一个 ByteArrayInputStream 用于读取字节数组
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);

        // 创建 HessianInput 实例，使用 ByteArrayInputStream 作为底层流
        HessianInput hi = new HessianInput(bis);

        // 使用 HessianInput 的 readObject 方法将字节流反序列化为对象
        // 需要将返回值强制转换为指定的类型 T
        return (T) hi.readObject(tClass);
    }
}
