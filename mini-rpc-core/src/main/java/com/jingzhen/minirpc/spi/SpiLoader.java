package com.jingzhen.minirpc.spi;

import cn.hutool.core.io.resource.ResourceUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SPI 加载器
 * 自定义实现，支持键值对映射
 * @author ZXZ
 */
@Slf4j
public class SpiLoader {

    /**
     * 存储已加载的类：接口名 =>（key => 实现类）。load方法加载的类会存储在这里。
     */
    private static final Map<String, Map<String, Class<?>>> LOADER_MAP = new ConcurrentHashMap<>();

    /**
     * 对象实例缓存（避免重复 new），类路径 => 对象实例，单例模式。getInstance方法会操作这个集合
     */
    private static final Map<String, Object> INSTANCE_CACHE = new ConcurrentHashMap<>();

    /**
     * 本框架提供的 SPI 目录
     */
    private static final String RPC_SYSTEM_SPI_DIR = "META-INF/rpc/system/";

    /**
     * 使用者自定义的 SPI 目录
     */
    private static final String RPC_CUSTOM_SPI_DIR = "META-INF/rpc/custom/";

    /**
     * 扫描路径。还有一个META-INF/services目录，这是Java SPI的标准目录。
     */
    private static final String[] SCAN_DIRS = new String[]{RPC_SYSTEM_SPI_DIR, RPC_CUSTOM_SPI_DIR};

    /**
     * 获取某个接口的实例
     * 该方法根据接口类和指定的 key 从 SPI 加载的实现类中获取对应的实例，并进行实例化。
     * 如果该实例已经被缓存，则直接返回缓存中的实例，避免重复创建。
     *
     * @param tClass 接口类的 `Class` 对象，用于查找对应的 SPI 实现类
     * @param key    该接口的 SPI 配置中的键（通常是某个具体实现的标识符）
     * @param <T>    返回的实例类型，通常是接口的类型
     * @return       返回指定 SPI 实现类的实例
     * @throws RuntimeException 如果在获取实例过程中发生错误（如未找到对应的 SPI 实现或实例化失败）
     */
    public static <T> T getInstance(Class<?> tClass, String key) {
        // 获取接口类的名称
        String tClassName = tClass.getName();

        // 从缓存中获取该接口对应的 SPI 配置信息（key -> Class 映射）
        Map<String, Class<?>> keyClassMap = LOADER_MAP.get(tClassName);

        // 如果未加载该接口的 SPI 配置信息，则抛出异常
        if (keyClassMap == null) {
            throw new RuntimeException(String.format("SpiLoader 未加载 %s 类型", tClassName));
        }

        // 如果指定的 key 不存在于 SPI 配置中，则抛出异常
        if (!keyClassMap.containsKey(key)) {
            throw new RuntimeException(String.format("SpiLoader 的 %s 不存在 key=%s 的类型", tClassName, key));
        }

        // 获取到要加载的实现类
        Class<?> implClass = keyClassMap.get(key);

        // 获取实现类的名称，用于缓存和实例化
        String implClassName = implClass.getName();

        // 如果实例缓存中没有该实现类的实例，则进行实例化
        if (!INSTANCE_CACHE.containsKey(implClassName)) {
            try {
                // 通过反射实例化实现类，并将实例放入缓存
                INSTANCE_CACHE.put(implClassName, implClass.newInstance());
            } catch (InstantiationException | IllegalAccessException e) {
                // 如果实例化失败，抛出运行时异常并附加错误信息
                String errorMsg = String.format("%s 类实例化失败", implClassName);
                throw new RuntimeException(errorMsg, e);
            }
        }

        // 从缓存中返回已经实例化的对象，并进行类型转换
        return (T) INSTANCE_CACHE.get(implClassName);
    }

    /**
     * 加载某个类型的 SPI 实现类
     * 该方法会扫描指定的目录，查找与 `loadClass` 类型相关的 SPI 配置文件，读取其中的类名并将它们映射为 `Class<?>` 类型。
     *
     * @param loadClass 需要加载 SPI 实现的类（通常是接口类）
     * @return           返回一个 `Map`，映射 SPI 配置中的 `key` 和对应的 `Class<?>` 类型
     */
    public static Map<String, Class<?>> load(Class<?> loadClass) {
        // 输出日志，表示正在加载指定类型的 SPI 实现类
        log.info("加载类型为 {} 的 SPI", loadClass.getName());

        // 创建一个 Map 用来存储 SPI 配置中的 key 和对应的 Class 类型
        // key：SPI 配置中的键（通常是接口方法名或标识符）
        // Class：SPI 配置中的值（通常是具体的实现类）
        Map<String, Class<?>> keyClassMap = new HashMap<>();

        // 遍历系统指定的扫描目录列表（SCAN_DIRS），这些目录包含了 SPI 配置文件
        for (String scanDir : SCAN_DIRS) {
            // 获取与 loadClass 类型名称匹配的 SPI 配置文件资源
            List<URL> resources = ResourceUtil.getResources(scanDir + loadClass.getName());

            // 遍历获取到的每个资源文件
            for (URL resource : resources) {
                try {
                    // 打开资源文件流，并将其包装为字符流进行读取
                    InputStreamReader inputStreamReader = new InputStreamReader(resource.openStream());
                    BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

                    String line;
                    // 逐行读取资源文件内容
                    while ((line = bufferedReader.readLine()) != null) {
                        // 使用 "=" 分隔符拆分每一行，得到键值对
                        String[] strArray = line.split("=");

                        // 确保当前行格式正确，即包含了一个键值对
                        if (strArray.length > 1) {
                            String key = strArray[0]; // 获取键
                            String className = strArray[1]; // 获取完整类名

                            // 将键和类名映射存入 Map 中，类名通过反射转换为 Class 对象
                            keyClassMap.put(key, Class.forName(className));
                        }
                    }
                } catch (Exception e) {
                    // 如果读取资源文件或加载类时发生异常，记录错误日志
                    log.error("spi resource load error", e);
                }
            }
        }

        // 将加载的 SPI 配置信息缓存到 loaderMap 中，以便后续快速访问
        LOADER_MAP.put(loadClass.getName(), keyClassMap);

        // 返回最终的 key -> Class 映射
        return keyClassMap;
    }
}
