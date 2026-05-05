package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.merchant.*;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MerchantMapper extends BatchBaseMapper<Merchant> {
    List<UpstreamDTO> selectByUpstreamSearchDTO(UpstreamSearchDTO params);

    List<DownstreamDTO> selectByDownstreamSearchDTO(DownstreamSearchDTO params);

    List<Merchant> selectInsuranceCompanyByAreaCode(String areaCode);
}
