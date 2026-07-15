package com.example.insurancesystem.domain.merchant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("biz_merchant_staff_role")
public class MerchantStaffRole {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long enterpriseId;
    private Long merchantId;
    private Long staffId;
    private String roleCode;
    private Integer isDefault;
    private Long updatedBy;
    @TableField("deleted")
    private Integer isDelete;
}
