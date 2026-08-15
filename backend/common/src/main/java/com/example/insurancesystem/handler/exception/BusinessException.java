package com.example.insurancesystem.handler.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
/**
 * 可由业务层主动抛出的统一异常，携带前端可识别的业务码、提示信息以及可选补充数据。
 * 全局异常处理器会保留这些字段，不把可预期业务失败误报为未知系统异常。
 */
public class BusinessException extends RuntimeException {

    // 响应码
    private Integer code;

    // 响应消息
    private String msg;

    private Object data;

    /**
     * 创建不带附加数据的业务异常，适用于参数、权限、状态冲突和资源不存在等常见失败。
     */
    public BusinessException(Integer code, String msg) {
        super(msg);
        this.code = code;
        this.msg = msg;
    }

    /**
     * 创建带结构化数据的业务异常，允许失败响应同时返回余额缺口、校验详情等前端后续处理信息。
     */
    public BusinessException(Integer code, String msg, Object data) {
        super(msg);
        this.code = code;
        this.msg = msg;
        this.data = data;
    }
}
