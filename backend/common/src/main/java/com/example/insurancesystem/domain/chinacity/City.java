package com.example.insurancesystem.domain.chinacity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
/**
 * 城市级行政区划节点，保存城市名称、代码及其下属区县集合。
 */
public class City {
    private String city;

    private String code;

    private List<Area> areas;
}
