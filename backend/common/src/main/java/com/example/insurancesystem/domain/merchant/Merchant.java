package com.example.insurancesystem.domain.merchant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@TableName(value="biz_merchant")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Merchant implements Serializable {
    private static final long serialVersionUID = -23465523L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    @TableField(exist = false)
    private String type;

    private Long categoryId;

    private Long enterpriseId;

    private String location;

    private String address;

    private String bank;

    private String bankCardNum;

    private String channel;

    private String servicePhone;

    private String contact;

    private String phone;

    private String defaultAreaCode;

    @TableField(value = "created_at", exist = false)
    private Long createTime;

    @TableField(value = "updated_at", exist = false)
    private Long updateTime;

    @TableField("updated_by")
    private Long updateBy;

    @TableField("deleted")
    private Integer isDelete;

    public Merchant(UpstreamDTO upstream){
        this.updateBy = upstream.getId();
        this.id = upstream.getId();
        this.code = upstream.getCode();
        this.location = upstream.getLocation();
        this.name = upstream.getName();
        this.type = upstream.getType();
        this.address = upstream.getAddress();
        this.contact = upstream.getContact();
        this.phone = upstream.getPhone();
        this.defaultAreaCode = upstream.getDefaultAreaCode();
    }

    public Merchant(DownstreamDTO downstream){
        this.id = downstream.getId();
        this.code = downstream.getCode();
        this.name = downstream.getName();
        this.type = downstream.getType();
        this.location = downstream.getLocation();
        this.address = downstream.getAddress();
        this.bank = downstream.getBank();
        this.bankCardNum = downstream.getBankCardNum();
        this.channel = downstream.getChannel();
        this.defaultAreaCode = downstream.getDefaultAreaCode();
        this.createTime = downstream.getCreateTime();
    }
}
