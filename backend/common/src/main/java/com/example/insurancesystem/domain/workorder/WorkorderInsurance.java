package com.example.insurancesystem.domain.workorder;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("biz_workorder_insurance")
public class WorkorderInsurance {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long enterpriseId;

    private Long workorderId;

    private Long insuranceId;

    @TableField(exist = false)
    private String insuranceName;

    private String optionJson;

    private String deductibleOptionJson;

    @TableField(exist = false)
    private Long createTime;

    @TableField(exist = false)
    private Long updateTime;

    @TableField("updated_by")
    private Long updateBy;

    @TableField("deleted")
    private Integer isDelete;
}
