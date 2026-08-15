package com.example.insurancesystem.domain.workorder;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@TableName(value = "biz_vehicle_invoice")
@AllArgsConstructor
@NoArgsConstructor
/**
 * 机动车销售发票实体，记录购方身份、车辆标识、开票金额及载重座位等投保资料。
 */
public class VehicleInvoice {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long enterpriseId;

    /**
     * 工单ID
     */
    private Long workorderId;

    /**
     * 发票金额
     */
    private BigDecimal invoiceAmount;

    /**
     * 购车人姓名
     */
    private String buyerName;

    /**
     * 购车人身份证号
     */
    private String buyerIdNum;

    /**
     * 车辆类型
     */
    private String vehicleType;

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
     * 座位数
     */
    private Integer seats;

    /**
     * 核定载质量（kg）
     */
    private Integer approvedLoadCapacity;

    /**
     * 创建时间（时间戳）
     */
    @TableField(exist = false)
    private Long createTime;

    /**
     * 更新时间（时间戳）
     */
    @TableField(exist = false)
    private Long updateTime;

    /**
     * 更新人ID
     */
    @TableField("updated_by")
    private Long updateBy;

    /**
     * 是否删除（0：未删除，1：已删除）
     */
    @TableField("deleted")
    private Integer isDelete;
}
