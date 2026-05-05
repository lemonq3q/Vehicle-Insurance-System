package com.example.insurancesystem.domain.workorder;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@TableName(value = "vehicle_certificate")
@AllArgsConstructor
@NoArgsConstructor
public class VehicleCertificate {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 工单ID
     */
    private Long workorderId;

    /**
     * 品牌型号
     */
    private String brandModel;

    /**
     * 车辆类型
     */
    private String vehicleType;

    /**
     * 车辆识别代码
     */
    private String vehicleCode;

    /**
     * 发动机号
     */
    private String engineCode;

    /**
     * 座位数
     */
    private Integer seats;

    /**
     * 整备质量（kg）
     */
    private Integer curbWeight;

    /**
     * 排量（mL）
     */
    private Integer displacement;

    /**
     * 核定载质量（kg）
     */
    private Integer approvedLoadCapacity;

    /**
     * 创建时间（时间戳）
     */
    private Long createTime;

    /**
     * 更新时间（时间戳）
     */
    private Long updateTime;

    /**
     * 更新人ID
     */
    private Long updateBy;

    /**
     * 是否删除（0：未删除，1：已删除）
     */
    private Integer isDelete;
}
