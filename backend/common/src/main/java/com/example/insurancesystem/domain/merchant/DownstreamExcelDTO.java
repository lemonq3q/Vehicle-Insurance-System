package com.example.insurancesystem.domain.merchant;

import com.alibaba.excel.annotation.ExcelProperty;
import com.example.insurancesystem.utils.AreaConverterUtil;
import com.example.insurancesystem.utils.TimeConvertUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.stream.Collectors;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DownstreamExcelDTO {

    @ExcelProperty("商家编号")
    private String code;

    @ExcelProperty("商家名称")
    private String name;

    @ExcelProperty("商家类型")
    private String type;

    @ExcelProperty("所在地区")
    private String location;

    @ExcelProperty("商家地址")
    private String address;

    @ExcelProperty("银行卡")
    private String bankAndCardNum;

//    @ExcelProperty("业务区域")
//    private String businessArea;
//
//    @ExcelProperty("所属渠道")
//    private String channel;

    @ExcelProperty("联系人")
    private String storeManager;

    @ExcelProperty("创建时间")
    private String createTime;

    public DownstreamExcelDTO(DownstreamDTO downstreamDTO) {
        this.code = downstreamDTO.getCode();
        this.name = downstreamDTO.getName();
        this.type = downstreamDTO.getType();
        this.location = AreaConverterUtil.areaCodeConvert(downstreamDTO.getLocation());
        this.address = downstreamDTO.getAddress();

        String bank = downstreamDTO.getBank();
        String bankCardNum = downstreamDTO.getBankCardNum();
        if (bank != null && bankCardNum != null) {
            this.bankAndCardNum = bank + "\n" + bankCardNum;
        }
        else if (bank != null) {
            this.bankAndCardNum = bank;
        }
        else {
            this.bankAndCardNum = bankCardNum;
        }

//        this.businessArea = downstreamDTO.getBusinessArea().stream()
//                .map(AreaConverterUtil::areaCodeConvert)
//                .collect(Collectors.joining(" , "));
//        this.channel = downstreamDTO.getChannel();
        this.storeManager = downstreamDTO.getStoreManager();
        if (downstreamDTO.getCreateTime() != null) {
            this.createTime = TimeConvertUtil.timestampConvert(downstreamDTO.getCreateTime());
        }
    }

}
