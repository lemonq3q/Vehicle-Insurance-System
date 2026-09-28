package com.example.insurancesystem.handler.exception;

/**
 * 明确要求以真实HTTP错误状态返回的业务失败，供回调及其他协议敏感接口主动声明失败。
 * 继承既有业务异常以保留code、msg和data语义，但由独立全局处理器转换HTTP响应。
 * 仅允许4xx/5xx状态；属于运行时异常，事务中抛出时继续遵循默认回滚规则。
 */
public class HttpBusinessException extends BusinessException {
    /**
     * 创建不带附加数据的协议级失败；status来自业务判断，不能用200表达失败。
     */
    public HttpBusinessException(int status, String message) {
        this(status, message, null);
    }

    /**
     * 创建保留附加业务数据的HTTP失败，状态非法时拒绝构造，防止意外返回成功状态。
     */
    public HttpBusinessException(int status, String message, Object data) {
        super(status, message, data);
        if (status < 400 || status > 599) throw new IllegalArgumentException("HTTP错误状态必须为400到599");
    }
}
