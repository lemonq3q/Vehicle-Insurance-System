package com.example.insurancesystem.domain.chinacity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class City {
    private String city;

    private String code;

    private List<Area> areas;
}
