package com.example.insurancesystem.domain.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchantUserDTO {
    private Long id;

    private String name;

    private String username;

    private String email;

    private String idNum;

    private Long roleId;

    private String roleName;

    private Long merchantId;

    private String merchantName;

    private String merchantCode;

    private Integer status;

    private Long createTime;

    public MerchantUserDTO(User user){
        this.id = user.getId();
        this.name = user.getName();
        this.username = user.getUsername();
        this.idNum = user.getIdNum();
        this.merchantId = user.getMerchantId();
        this.status = user.getStatus();
        this.createTime = user.getCreateTime();
    }
}
