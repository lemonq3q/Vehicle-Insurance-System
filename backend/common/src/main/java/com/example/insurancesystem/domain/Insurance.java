package com.example.insurancesystem.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("biz_insurance_product")
public class Insurance {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long enterpriseId;

    private String name;

    private Integer type;

    private String optionsJson;

    private String defaultOptionJson;

    private String deductibleOptionsJson;

    private String defaultDeductibleOptionJson;

    private String remark;

    @TableField(exist = false)
    private Long createTime;

    @TableField(exist = false)
    private Long updateTime;

    @TableField("updated_by")
    private Long updateBy;

    @TableField("deleted")
    private Integer isDelete;
}
