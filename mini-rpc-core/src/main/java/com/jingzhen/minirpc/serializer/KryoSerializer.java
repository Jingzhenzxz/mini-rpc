package com.jingzhen.minirpc.serializer;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/**
 * Kryo 序列化器
 * <p>
 * 本类实现了 `Serializer` 接口，使用 Kryo 库来进行对象的序列化和反序列化。Kryo 是一个高效的 Java 序列化工具，它通过直接生成字节流而不是使用传统的 Java 序列化方式来提高性能。
 * <p>
 * 由于 Kryo 不是线程安全的，所以使用 `ThreadLocal` 确保每个线程都有独立的 `Kryo` 实例，避免多线程并发使用同一实例时出现线程安全问题。
 */
public class KryoSerializer implements Serializer {

    /**
     * Kryo 是线程不安全的，为了确保每个线程拥有独立的 Kryo 实例，使用 ThreadLocal 进行线程隔离
     * 这样每个线程都会持有一个 Kryo 实例，避免线程安全问题。
     */
    private static final ThreadLocal<Kryo> KRYO_THREAD_LOCAL = ThreadLocal.withInitial(() -> {
        // 初始化一个 Kryo 实例
        Kryo kryo = new Kryo();
        // 设置 Kryo 不强制要求所有类在序列化时必须提前注册（默认情况下需要提前注册类来保证性能）
        // 由于这里的序列化可能是动态的，因此不提前注册所有类，这样更灵活，但也可能带来一定的安全隐患
        kryo.setRegistrationRequired(false);
        return kryo;
    });

    /**
     * 序列化方法
     * <p>
     * 将传入的对象序列化为字节数组。序列化是将对象转换为字节流，方便存储或通过网络传输。
     *
     * @param obj 要序列化的对象
     * @param <T> 对象的类型
     * @return 序列化后的字节数组
     */
    @Override
    public <T> byte[] serialize(T obj) {
        // 创建字节输出流，将序列化的字节数据写入其中
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        // 创建 Kryo 输出流
        Output output = new Output(byteArrayOutputStream);
        // 获取当前线程的 Kryo 实例并进行对象序列化。输出是流+对象，输入是流+类型。
        KRYO_THREAD_LOCAL.get().writeObject(output, obj);
        // 关闭输出流
        output.close();
        // 返回序列化后的字节数组
        return byteArrayOutputStream.toByteArray();
    }

    /**
     * 反序列化方法
     * <p>
     * 将字节数组反序列化为指定类型的 Java 对象。反序列化是将字节流恢复为原始的 Java 对象。
     *
     * @param bytes     序列化后的字节数组
     * @param classType 反序列化的目标类型
     * @param <T>       反序列化后的对象类型
     * @return 反序列化后的对象
     */
    @Override
    public <T> T deserialize(byte[] bytes, Class<T> classType) {
        // 创建字节输入流，从字节数组中读取数据
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        // 创建 Kryo 输入流
        Input input = new Input(byteArrayInputStream);
        // 获取当前线程的 Kryo 实例并进行对象反序列化
        T result = KRYO_THREAD_LOCAL.get().readObject(input, classType);
        // 关闭输入流
        input.close();
        // 返回反序列化后的对象
        return result;
    }
}