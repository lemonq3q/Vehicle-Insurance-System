package com.example.insurancesystem.domain.merchant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName(value="biz_merchant_area")
@Data
@AllArgsConstructor
@NoArgsConstructor
/**
 * 商户与服务行政区域的关联实体，使一个合作机构可以覆盖多个省市区范围。
 */
public class MerchantArea {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    private Long enterpriseId;

    private String areaCode;

    @TableField(exist = false)
    private Long createTime;

    @TableField(exist = false)
    private Long updateTime;

    @TableField("updated_by")
    private Long updateBy;

    @TableField("deleted")
    private Integer isDelete;

}
