package com.jingzhen.minirpc.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jingzhen.minirpc.model.RpcRequest;
import com.jingzhen.minirpc.model.RpcResponse;

import java.io.IOException;

/**
 * Json 序列化器
 * <p>
 * 本类实现了 `Serializer` 接口，使用 Jackson 的 `ObjectMapper` 实现 JSON 格式的序列化和反序列化。
 * 它将 Java 对象转换为 JSON 字节数组进行传输，并能够根据传入的字节数组恢复出原始对象。
 * 特别地，针对 RPC 请求和响应的反序列化进行了特殊处理，以确保参数和数据的类型匹配。
 */
public class JsonSerializer implements Serializer {

    // Jackson ObjectMapper 实例，用于处理 JSON 的序列化和反序列化
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 将 Java 对象序列化为字节数组
     *
     * @param obj 要序列化的 Java 对象
     * @param <T> 对象的类型
     * @return 序列化后的字节数组
     * @throws IOException 如果在序列化过程中发生 I/O 异常
     */
    @Override
    public <T> byte[] serialize(T obj) throws IOException {
        // 使用 ObjectMapper 将对象转换为字节数组（JSON）
        return OBJECT_MAPPER.writeValueAsBytes(obj);
    }

    /**
     * 将字节数组反序列化为指定类型的对象
     *
     * @param bytes     序列化后的字节数组
     * @param classType 目标类型的 Class 对象
     * @param <T>       对象的类型
     * @return 反序列化后的 Java 对象
     * @throws IOException 如果在反序列化过程中发生 I/O 异常
     */
    @Override
    public <T> T deserialize(byte[] bytes, Class<T> classType) throws IOException {
        // 使用 ObjectMapper 将字节数组反序列化为目标类型的对象
        T obj = OBJECT_MAPPER.readValue(bytes, classType);

        // 如果反序列化的对象是 RpcRequest 类型，则做特殊处理
        if (obj instanceof RpcRequest) {
            return handleRequest((RpcRequest) obj, classType);
        }

        // 如果反序列化的对象是 RpcResponse 类型，则做特殊处理
        if (obj instanceof RpcResponse) {
            return handleResponse((RpcResponse) obj, classType);
        }

        // 如果是其他类型，直接返回
        return obj;
    }

    /**
     * 特殊处理 RPC 请求的反序列化，确保参数类型匹配
     * <p>
     * 由于反序列化时 Object 类型会被转换为 `LinkedHashMap`，这会导致类型擦除问题，因此需要对每个参数进行重新转换。
     *
     * @param rpcRequest RPC 请求对象
     * @param type       请求的目标类型
     * @param <T>        对象的类型
     * @return 反序列化后的 RPC 请求对象
     * @throws IOException 如果发生 I/O 异常
     */
    private <T> T handleRequest(RpcRequest rpcRequest, Class<T> type) throws IOException {
        // 获取 RPC 请求中的参数类型和参数值
        Class<?>[] parameterTypes = rpcRequest.getParameterTypes();
        Object[] args = rpcRequest.getArgs();

        // 循环处理每个参数的类型，确保参数类型匹配
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> clazz = parameterTypes[i];

            // 如果参数的实际类型与预期的类型不匹配，则进行类型转换
            if (!clazz.isAssignableFrom(args[i].getClass())) {
                // 将参数对象序列化为字节数组，再反序列化为目标类型的对象
                byte[] argBytes = OBJECT_MAPPER.writeValueAsBytes(args[i]);
                args[i] = OBJECT_MAPPER.readValue(argBytes, clazz);
            }
        }

        // 将处理后的 RpcRequest 对象返回
        return type.cast(rpcRequest);
    }

    /**
     * 特殊处理 RPC 响应的反序列化，确保响应数据类型匹配
     * <p>
     * 由于反序列化时 Object 类型会被转换为 `LinkedHashMap`，这会导致类型擦除问题，因此需要确保响应数据的类型被正确转换。
     *
     * @param rpcResponse RPC 响应对象
     * @param type        响应的目标类型
     * @param <T>         对象的类型
     * @return 反序列化后的 RPC 响应对象
     * @throws IOException 如果发生 I/O 异常
     */
    private <T> T handleResponse(RpcResponse rpcResponse, Class<T> type) throws IOException {
        // 获取 RPC 响应的数据部分，并将其序列化为字节数组
        byte[] dataBytes = OBJECT_MAPPER.writeValueAsBytes(rpcResponse.getData());

        // 反序列化数据部分为预期的目标类型
        rpcResponse.setData(OBJECT_MAPPER.readValue(dataBytes, rpcResponse.getDataType()));

        // 返回处理后的 RPC 响应对象
        return type.cast(rpcResponse);
    }
}