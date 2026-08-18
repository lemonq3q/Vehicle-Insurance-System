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

    /**
     * 续保周期天数，由服务端设置，当前固定为 365 天；不接受前端覆盖。
     */
    private Integer renewalCycleDays;

    /**
     * 每个续保周期结束前的提醒天数，由服务端设置，当前固定为 30 天；不接受前端覆盖。
     */
    private Integer renewalAdvanceDays;

    private Integer pageSize;

    private Integer pageNum;
}
