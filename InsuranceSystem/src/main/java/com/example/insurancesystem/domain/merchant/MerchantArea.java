package com.example.insurancesystem.domain.merchant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@TableName(value="merchant_area")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MerchantArea {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    private String areaCode;

    private Long createTime;

    private Long updateTime;

    private Long updateBy;

    private Integer isDelete;

}
