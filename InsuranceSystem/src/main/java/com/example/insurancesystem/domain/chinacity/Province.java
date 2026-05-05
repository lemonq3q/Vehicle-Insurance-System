package com.example.insurancesystem.domain.chinacity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Province {
    private String province;

    private String code;

    private List<City> citys;
}
