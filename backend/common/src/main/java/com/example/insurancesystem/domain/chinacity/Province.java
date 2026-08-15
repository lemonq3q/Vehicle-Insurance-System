package com.example.insurancesystem.domain.chinacity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
/**
 * 省级行政区划根节点，保存省份名称、代码及下属城市树，供地址级联选择和代码转换使用。
 */
public class Province {
    private String province;

    private String code;

    private List<City> citys;
}
