package com.example.insurancesystem.domain.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "tenant_user")
public class User implements Serializable {
    private static final long serialVersionUID = -42345534L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String email;

    private String password;

    private String realName;

    private String idNum;

    private Integer status;

    @TableField(exist = false)
    private Long merchantId;

    @TableField(exist = false)
    private Integer isApproval;

    @TableField(exist = false)
    private Long createTime;

    @TableField(exist = false)
    private Long updateTime;

    @TableField("updated_by")
    private Long updateBy;

    @TableField("deleted")
    private Integer isDelete;

    @TableField(exist = false)
    private String roleName;

    public User(MerchantUserDTO merchantUserDTO){
        this.id = merchantUserDTO.getId();
        this.username = merchantUserDTO.getUsername();
        this.email = merchantUserDTO.getEmail();
        this.realName = merchantUserDTO.getName();
        this.idNum = merchantUserDTO.getIdNum();
        this.status = merchantUserDTO.getStatus();
        this.merchantId = merchantUserDTO.getMerchantId();
        this.createTime = merchantUserDTO.getCreateTime();
    }

}
