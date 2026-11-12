package com.example.insurancesystem.ai;

/**
 * 承保 OCR 请求只携带工单和数据库文件 ID。
 * 模型范围始终覆盖三种险种，调用方不能传入外部地址或以附件类型限制识别范围。
 *
 * @param workorderId 当前有权编辑的工单 ID
 * @param fileId 已完成 OSS 上传的系统文件 ID
 */
public record PolicyOcrRequest(Long workorderId, Long fileId) {
}
