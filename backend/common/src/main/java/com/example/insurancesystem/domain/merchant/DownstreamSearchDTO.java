package com.example.insurancesystem.domain.merchant;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DownstreamSearchDTO {
    private Long id;

    private String blurParam;

    private String location;

    private String businessArea;

    private String channel;

    private Integer pageSize;

    private Integer pageNum;
}
