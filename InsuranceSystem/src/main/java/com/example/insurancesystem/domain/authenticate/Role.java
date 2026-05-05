package com.example.insurancesystem.domain.authenticate;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "role")
public class Role {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Integer status;

    private Integer isDelete;

    private Long createTime;

    private Long updateTime;

    private Long updateBy;

    private String remark;
}
