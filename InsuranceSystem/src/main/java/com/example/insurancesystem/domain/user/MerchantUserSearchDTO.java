package com.example.insurancesystem.domain.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MerchantUserSearchDTO {
    private String blurParam;

    private Long merchantId;

    private Long roleId;

    private Integer status;

    private Integer pageSize;

    private Integer pageNum;
}
