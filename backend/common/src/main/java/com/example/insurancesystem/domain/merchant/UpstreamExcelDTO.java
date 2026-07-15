package com.example.insurancesystem.domain.merchant;

import com.alibaba.excel.annotation.ExcelProperty;
import com.example.insurancesystem.utils.AreaConverterUtil;
import com.example.insurancesystem.utils.TimeConvertUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpstreamExcelDTO {

    @ExcelProperty("机构编号")
    private String code;

    @ExcelProperty("机构名称")
    private String name;

    @ExcelProperty("机构类型")
    private String type;

    @ExcelProperty("机构所在地区")
    private String location;

    @ExcelProperty("机构地址")
    private String address;

    @ExcelProperty("联系人")
    private String contact;

    @ExcelProperty("联系电话")
    private String phone;

//    @ExcelProperty("业务渠道")
//    private String businessChannel;

    @ExcelProperty("业务区域")
    private String businessArea;

//    @ExcelProperty("默认业务区域")
//    private String defaultAreaCode;

    @ExcelProperty("创建时间")
    private String createTime;

    public UpstreamExcelDTO(UpstreamDTO upstreamDTO){
        this.code = upstreamDTO.getCode();
        this.name = upstreamDTO.getName();
        this.type = upstreamDTO.getType();
        this.location = AreaConverterUtil.areaCodeConvert(upstreamDTO.getLocation());
        this.address = upstreamDTO.getAddress();
        this.contact = upstreamDTO.getContact();
        this.phone = upstreamDTO.getPhone();
        if (upstreamDTO.getCreateTime() != null) {
            this.createTime = TimeConvertUtil.timestampConvert(upstreamDTO.getCreateTime());
        }
//        this.defaultAreaCode = AreaConverterUtil.areaCodeConvert(upstreamDTO.getDefaultAreaCode());
        if (upstreamDTO.getBusinessArea() != null){
            this.businessArea = upstreamDTO.getBusinessArea().stream()
                    .map(AreaConverterUtil::areaCodeConvert)
                    .collect(Collectors.joining(" , "));
        }
//        if (upstreamDTO.getBusinessChannel() != null){
//            this.businessChannel = String.join(" , ", upstreamDTO.getBusinessChannel());
//        }
    }
}
