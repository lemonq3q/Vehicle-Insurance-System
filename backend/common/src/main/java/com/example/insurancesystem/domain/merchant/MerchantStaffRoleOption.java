package com.example.insurancesystem.domain.merchant;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
/**
 * 商户角色下拉选项模型，只暴露角色代码和名称供前端分配员工职责。
 */
public class MerchantStaffRoleOption {
    private Long id;
    private String code;
    private String name;
}
