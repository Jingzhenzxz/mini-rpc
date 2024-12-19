package com.jingzhen.minirpc.exception;

/**
 * 自定义异常类
 */
public class RpcException extends RuntimeException {

    public RpcException(String message) {
        super(message);
    }

}
