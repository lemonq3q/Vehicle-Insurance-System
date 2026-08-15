package com.example.insurancesystem.domain.merchant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
/**
 * 上游渠道分页查询条件，承载机构关键字、地区和状态等可选过滤项。
 */
public class UpstreamSearchDTO {
    private Long id;

    private String blurParam;

    private String location;

    private String businessArea;

    private String businessChannel;

    private Integer pageSize;

    private Integer pageNum;
}
