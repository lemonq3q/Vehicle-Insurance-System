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
/**
 * 实现车险业务中的商户基础查询，主要负责商户服务区域与区域内保险公司的匹配。
 */
public class MerchantServiceImpl implements MerchantService {

    @Autowired
    private MerchantAreaMapper merchantAreaMapper;

    @Autowired
    private MerchantMapper merchantMapper;

    @Override
    /**
     * 查询指定商户当前有效的服务区域，过滤已经逻辑删除的区域关系。
     *
     * @param id 商户主键
     * @return 商户可开展业务的区域列表
     */
    public ResponseResult selectAreaByMerchantId(Long id) {
        LambdaQueryWrapper<MerchantArea> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MerchantArea::getMerchantId, id);
        wrapper.eq(MerchantArea::getIsDelete, 0);
        List<MerchantArea> merchantAreas = merchantAreaMapper.selectList(wrapper);
        return new ResponseResult(200, merchantAreas);
    }

    @Override
    /**
     * 根据行政区域编码查询能够在该区域承保的保险公司。
     * 区域与保险公司的关联筛选集中在 Mapper 查询中完成。
     *
     * @param areaCode 行政区域编码
     * @return 区域内可选的保险公司列表
     */
    public ResponseResult selectInsuranceCompanyByArea(String areaCode) {
        List<Merchant> merchants = merchantMapper.selectInsuranceCompanyByAreaCode(areaCode);
        return new ResponseResult(200, merchants);
    }
}
