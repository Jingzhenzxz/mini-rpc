package com.jingzhen.minirpc.serializer;

import java.io.*;

/**
 * JDK 序列化器
 * 本类实现了 `Serializer` 接口，使用 JDK 原生的序列化机制来完成对象的序列化和反序列化操作。
 * JDK 序列化机制基于 Java 原生的 `ObjectOutputStream` 和 `ObjectInputStream` 实现对象的字节流转换。
 */
public class JdkSerializer implements Serializer {

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
        // 创建一个字节输出流，用于保存序列化后的字节数据
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // 创建一个 ObjectOutputStream，负责将对象写入字节流
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);

        // 将对象序列化写入字节流
        objectOutputStream.writeObject(object);

        // 关闭 ObjectOutputStream
        objectOutputStream.close();

        // 返回字节数组（序列化后的数据）
        return outputStream.toByteArray();
    }

    /**
     * 反序列化方法，将字节数组恢复为对象
     *
     * @param bytes 序列化后的字节数组
     * @param type 目标对象的类类型
     * @param <T>  对象的类型
     * @return 反序列化后的对象
     * @throws IOException 如果反序列化过程中发生 I/O 异常
     */
    @Override
    public <T> T deserialize(byte[] bytes, Class<T> type) throws IOException {
        // 创建一个字节输入流，用于读取字节数组
        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);

        // 创建一个 ObjectInputStream，负责从字节流中读取对象
        ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);

        try {
            // 从字节流中读取对象，并强制转换为目标类型
            return (T) objectInputStream.readObject();
        } catch (ClassNotFoundException e) {
            // 如果类无法找到，抛出运行时异常
            throw new RuntimeException(e);
        } finally {
            // 关闭 ObjectInputStream，释放资源
            objectInputStream.close();
        }
    }
}