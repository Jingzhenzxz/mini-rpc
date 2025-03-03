package com.jingzhen.minirpc.fault.tolerant;

import com.jingzhen.minirpc.model.RpcResponse;

import java.util.Map;

/**
 * 快速失败 - 容错策略（立刻通知外层调用方）
 * @author ZXZ
 */
public class FailFastTolerantStrategy implements TolerantStrategy {

    @Override
    public RpcResponse doTolerant(Map<String, Object> context, Exception e) {
        throw new RuntimeException("服务报错", e);
    }
}
