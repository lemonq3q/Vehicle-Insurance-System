package com.example.insurancesystem.domain.merchant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
/**
 * 下游机构列表查询条件，承载关键字、区域、状态和分页等可选筛选字段。
 */
public class DownstreamSearchDTO {
    private Long id;

    private String blurParam;

    private String location;

    private String businessArea;

    private String channel;

    private Integer pageSize;

    private Integer pageNum;
}
