package com.example.insurancesystem.domain.authenticate;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "auth_role")
public class Role {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Integer status;

    @TableField("deleted")
    private Integer isDelete;

    @TableField(exist = false)
    private Long createTime;

    @TableField(exist = false)
    private Long updateTime;

    @TableField(exist = false)
    private Long updateBy;

    private String remark;
}
