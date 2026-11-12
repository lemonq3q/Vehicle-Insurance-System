package com.example.insurancesystem.domain.workorder;

import lombok.Data;

/**
 * 表达一次续保处理意图；服务端自行计算周期并验证权限，不接收客户端指定的完成周期。
 * 新页面分别提交 commercialStatus、compulsoryStatus，可在一次事务内保存不同结果；
 * 旧客户端的 COMMERCIAL/COMPULSORY/ALL 加 status 仍继续受理。
 * 新表单状态 0 为未处理、1 为已续保、2 为已流失；旧范围接口仅接受 1、2。
 * 永久关闭由独立接口和前端警告确认处理。
 */
@Data
public class RenewalFollowUpRequest {
    private String scope;
    private Integer status;
    private Integer commercialStatus;
    private Integer compulsoryStatus;
    private String followUpRes;
}
