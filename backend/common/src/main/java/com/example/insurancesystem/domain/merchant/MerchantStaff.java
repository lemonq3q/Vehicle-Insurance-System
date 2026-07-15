package com.example.insurancesystem.domain.merchant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("biz_merchant_staff")
public class MerchantStaff {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long enterpriseId;
    private Long merchantId;
    private String name;
    private String phone;
    private String email;
    private String idNum;
    private Integer status;
    private String remark;
    private Long updatedBy;
    @TableField("deleted")
    private Integer isDelete;
}
