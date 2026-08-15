package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.workorder.VehicleCertificate;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 维护新车工单关联的车辆合格证识别及录入信息。
 */
public interface VehicleCertificateMapper extends BatchBaseMapper<VehicleCertificate> {
}
