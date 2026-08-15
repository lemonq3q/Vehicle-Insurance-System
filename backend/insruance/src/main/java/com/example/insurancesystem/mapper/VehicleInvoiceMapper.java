package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.workorder.VehicleInvoice;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 维护新车工单关联的机动车销售发票信息。
 */
public interface VehicleInvoiceMapper extends BatchBaseMapper<VehicleInvoice> {
}
