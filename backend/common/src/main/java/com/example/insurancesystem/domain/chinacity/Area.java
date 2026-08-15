package com.example.insurancesystem.domain.chinacity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * 区县级行政区划节点，保存区县名称和代码，是城市节点的最末级集合元素。
 */
public class Area {
    private String area;

    private String code;
}
