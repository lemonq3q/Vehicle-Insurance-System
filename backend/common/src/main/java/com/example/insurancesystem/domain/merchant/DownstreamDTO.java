package com.example.insurancesystem.domain.merchant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
/**
 * 下游合作机构详情传输对象，聚合商户基础资料、区域和联系人信息供车险业务页面展示与编辑。
 */
public class DownstreamDTO {
    private Long id;

    private String code;

    private String name;

    private String type;

    private String location;

    private String address;

    private String bank;

    private String bankCardNum;

    private String channel;

    private String defaultAreaCode;

    private String contact;

    private String phone;

    private Long createTime;

    private List<String> businessArea;

    public DownstreamDTO(Merchant merchant) {
        this.id = merchant.getId();
        this.code = merchant.getCode();
        this.name = merchant.getName();
        this.contact = merchant.getContact();
        this.phone = merchant.getPhone();
        this.type = merchant.getType();
        this.location = merchant.getLocation();
        this.address = merchant.getAddress();
        this.bank = merchant.getBank();
        this.bankCardNum = merchant.getBankCardNum();
        this.channel = merchant.getChannel();
        this.defaultAreaCode = merchant.getDefaultAreaCode();
        this.createTime = merchant.getCreateTime();
    }
}
