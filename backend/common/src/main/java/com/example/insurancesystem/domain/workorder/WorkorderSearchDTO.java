package com.example.insurancesystem.domain.workorder;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
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
