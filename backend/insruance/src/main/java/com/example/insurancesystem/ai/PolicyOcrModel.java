package com.example.insurancesystem.ai;

import java.util.List;

/**
 * 定义保单识别业务对模型供应商的两项能力：读取附件和整理已识别文本。
 * 阿里、豆包与 Gemini 的请求协议由各自实现处理，异步任务和校验流程只依赖本接口。
 * 实现由应用启动配置选定，单个任务的识别与整理始终使用同一供应商。
 */
public interface PolicyOcrModel {
    /**
     * 读取已通过企业与文件校验的保单附件，并按共用提示词提取业务字段。
     *
     * @param url 服务端签发的临时 OSS 下载地址
     * @param pdf 文件是否为 PDF；否则按图片处理
     * @return 模型返回的原始文本，后续须由本地校验
     */
    String recognize(String url, boolean pdf);

    /**
     * 使用首次识别原文和全部校验问题进行纯文本整理，不重新读取附件。
     *
     * @param raw 首次识别结果，不包含前一次整理结果
     * @param errors 本轮本地校验产生的完整问题列表
     * @return 整理后的 JSON 文本，仍须再次校验
     */
    String organize(String raw, List<String> errors);
}
