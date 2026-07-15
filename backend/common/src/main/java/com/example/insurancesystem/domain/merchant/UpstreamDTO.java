package com.example.insurancesystem.domain.merchant;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpstreamDTO{

    private Long id;

    private String code;

    private String name;

    private String type;

    private String location;

    private String address;

    private String contact;

    private String phone;

    private Long createTime;

    private String defaultAreaCode;

    private List<String> businessArea = new ArrayList<>();

    private List<String> businessChannel = new ArrayList<>();

    public UpstreamDTO(Merchant merchant) {
        this.id = merchant.getId();
        this.code = merchant.getCode();
        this.name = merchant.getName();
        this.type = merchant.getType();
        this.location = merchant.getLocation();
        this.address = merchant.getAddress();
        this.contact = merchant.getContact();
        this.phone = merchant.getPhone();
        this.createTime = merchant.getCreateTime();
        this.defaultAreaCode = merchant.getDefaultAreaCode();
    }

}
