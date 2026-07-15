package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.workorder.VehicleLicense;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VehicleLicenseMapper extends BatchBaseMapper<VehicleLicense> {
}
