package com.example.insurancesystem.domain.workorder;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@TableName(value = "vehicle_license")
@AllArgsConstructor
@NoArgsConstructor
public class VehicleLicense {
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
     * 车牌号
     */
    private String licensePlate;

    /**
     * 车辆类型
     */
    private String vehicleType;

    /**
     * 所有人姓名
     */
    private String ownerName;

    /**
     * 使用性质
     */
    private String usageNature;

    /**
     * 品牌型号
     */
    private String brandModel;

    /**
     * 车辆识别代码
     */
    private String vehicleCode;

    /**
     * 发动机号
     */
    private String engineCode;

    /**
     * 注册日期（时间戳）
     */
    private Long registrationDate;

    /**
     * 发证日期（时间戳）
     */
    private Long issueDate;

    /**
     * 座位数
     */
    private Integer seats;

    /**
     * 核定载质量（kg）
     */
    private Integer approvedLoadCapacity;

    /**
     * 整备质量（kg）
     */
    private Integer curbWeight;

    /**
     * 是否过户（0：未过户，1：已过户）
     */
    private Integer isTransfer;

    /**
     * 过户日期（时间戳）
     */
    private Long transferDate;

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
