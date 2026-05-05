package com.example.insurancesystem.domain.user;

import com.alibaba.excel.annotation.ExcelProperty;
import com.example.insurancesystem.utils.TimeConvertUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchantUserExcelDTO {

    @ExcelProperty("用户名称")
    private String name;

    @ExcelProperty("手机号码")
    private String username;

    @ExcelProperty("身份证号")
    private String idNum;

    @ExcelProperty("用户角色")
    private String roleName;

    @ExcelProperty("所属商家")
    private String merchantName;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("创建时间")
    private String createTime;

    public MerchantUserExcelDTO(MerchantUserDTO merchantUserDTO){
        this.name = merchantUserDTO.getName();
        this.username = merchantUserDTO.getUsername();
        this.idNum = merchantUserDTO.getIdNum();
        this.roleName = merchantUserDTO.getRoleName();
        this.merchantName = merchantUserDTO.getMerchantName();
        this.status = merchantUserDTO.getStatus() == 1 ? "有效":"无效";
        if (merchantUserDTO.getCreateTime() != null){
            this.createTime = TimeConvertUtil.timestampConvert(merchantUserDTO.getCreateTime());
        }
    }
}
