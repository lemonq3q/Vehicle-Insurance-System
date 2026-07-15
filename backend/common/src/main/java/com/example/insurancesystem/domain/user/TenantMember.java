package com.example.insurancesystem.domain.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("tenant_member")
public class TenantMember {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long enterpriseId;
    private Long userId;
    private String roleCode;
    private Integer status;
    private Long updatedBy;
    @TableField("deleted")
    private Integer isDelete;
}
