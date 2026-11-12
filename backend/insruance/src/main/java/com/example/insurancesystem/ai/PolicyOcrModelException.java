package com.example.insurancesystem.ai;

/**
 * 表示 Gemini 原生接口调用失败，并保留 HTTP 状态供文件识别重试规则判断。
 * 状态码 0 表示连接或读取失败；非临时 4xx 不重试，异常不包含密钥或签名地址。
 */
public class PolicyOcrModelException extends RuntimeException {
    private final int statusCode;

    /**
     * 将供应商错误转为现有业务重试器可识别的状态，不暴露请求中的私有附件地址。
     *
     * @param statusCode HTTP 状态；网络故障时传 0
     * @param message 可安全记录的错误摘要
     * @param cause 原始网络或解析异常，可为空
     */
    public PolicyOcrModelException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /**
     * 提供模型接口的 HTTP 状态，供调用器仅重试临时故障。
     *
     * @return HTTP 状态码，0 表示连接故障
     */
    public int statusCode() {
        return statusCode;
    }
}
