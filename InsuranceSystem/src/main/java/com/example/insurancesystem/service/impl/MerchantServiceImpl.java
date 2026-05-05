package com.example.insurancesystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.merchant.Merchant;
import com.example.insurancesystem.domain.merchant.MerchantArea;
import com.example.insurancesystem.mapper.MerchantAreaMapper;
import com.example.insurancesystem.mapper.MerchantMapper;
import com.example.insurancesystem.service.MerchantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MerchantServiceImpl implements MerchantService {

    @Autowired
    private MerchantAreaMapper merchantAreaMapper;

    @Autowired
    private MerchantMapper merchantMapper;

    @Override
    public ResponseResult selectAreaByMerchantId(Long id) {
        LambdaQueryWrapper<MerchantArea> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MerchantArea::getMerchantId, id);
        wrapper.eq(MerchantArea::getIsDelete, 0);
        List<MerchantArea> merchantAreas = merchantAreaMapper.selectList(wrapper);
        return new ResponseResult(200, merchantAreas);
    }

    @Override
    public ResponseResult selectInsuranceCompanyByArea(String areaCode) {
        List<Merchant> merchants = merchantMapper.selectInsuranceCompanyByAreaCode(areaCode);
        return new ResponseResult(200, merchants);
    }
}
