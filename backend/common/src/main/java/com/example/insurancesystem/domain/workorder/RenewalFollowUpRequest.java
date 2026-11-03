package com.example.insurancesystem.domain.workorder;

import lombok.Data;

/**
 * 表达一次续保处理意图；服务端自行计算周期并验证权限，不接收客户端指定的完成周期。
 * COMMERCIAL/COMPULSORY 分别处理单险种，ALL 同时提前完成另一险种本轮任务。
 * 状态 1 为已续保，2 为已流失；永久关闭由独立接口和前端警告确认处理。
 */
@Data
public class RenewalFollowUpRequest {
    private String scope;
    private Integer status;
    private String followUpRes;
}
