package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.workorder.VehicleLicense;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 维护在用车辆工单关联的机动车行驶证信息。
 */
public interface VehicleLicenseMapper extends BatchBaseMapper<VehicleLicense> {
}
