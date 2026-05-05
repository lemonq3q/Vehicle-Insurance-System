package com.example.insurancesystem.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("insurance")
public class Insurance {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Integer type;

    private String optionsJson;

    private String defaultOptionJson;

    private String deductibleOptionsJson;

    private String defaultDeductibleOptionJson;

    private String remark;

    private Long createTime;

    private Long updateTime;

    private Long updateBy;

    private Integer isDelete;
}
