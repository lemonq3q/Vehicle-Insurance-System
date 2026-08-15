package com.example.insurancesystem.domain.workorder;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * 工单列表与续保查询条件，承载工单号、客户、机构、状态、时间范围和分页等筛选字段。
 */
public class WorkorderSearchDTO {
    private Long id;

    private String blurParam;

    private Long beginTime;

    private Long endTime;

    private String dateType;

    private Long createMerchantId;

    private Long handleMerchantId;

    private Long handleUserId;

    private Long createBy;

    private String areaCode;

    private Long insuranceId;

    private Integer status;

    private Integer remindStatus;

    private Integer renewalRemindDays;

    private Integer pageSize;

    private Integer pageNum;
}
