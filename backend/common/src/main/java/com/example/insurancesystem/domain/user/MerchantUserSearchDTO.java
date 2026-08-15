package com.example.insurancesystem.domain.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
/**
 * 商户员工分页查询条件，支持按账号、姓名、机构、角色和状态筛选。
 */
public class MerchantUserSearchDTO {
    private String blurParam;

    private Long merchantId;

    private Long roleId;

    private Integer status;

    private Integer pageSize;

    private Integer pageNum;
}
