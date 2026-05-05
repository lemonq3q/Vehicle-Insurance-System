package com.example.insurancesystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.encapsulate.TableData;
import com.example.insurancesystem.domain.merchant.*;
import com.example.insurancesystem.domain.user.MerchantUserDTO;
import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.mapper.MerchantAreaMapper;
import com.example.insurancesystem.mapper.MerchantMapper;
import com.example.insurancesystem.mapper.UserMapper;
import com.example.insurancesystem.mapper.UserRoleMapper;
import com.example.insurancesystem.service.DownstreamService;
import com.example.insurancesystem.service.UserService;
import com.example.insurancesystem.utils.SystemCommonUtil;
import com.github.pagehelper.PageHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class DownstreamServiceImp implements DownstreamService {

    @Autowired
    private MerchantMapper merchantMapper;

    @Autowired
    private MerchantAreaMapper merchantAreaMapper;

    @Autowired
    private UserService userService;

    @Override
    public ResponseResult select(DownstreamSearchDTO params) {
        PageHelper.startPage(params.getPageNum(), params.getPageSize());
        List<DownstreamDTO> downstreamDTOList = merchantMapper.selectByDownstreamSearchDTO(params);
        TableData<DownstreamDTO> tableData = new TableData<>(downstreamDTOList);

        if (downstreamDTOList == null || downstreamDTOList.isEmpty()) {
            return new ResponseResult(200, tableData);
        }

        List<Long> ids = tableData.getTable().stream().map(DownstreamDTO::getId).toList();
        LambdaQueryWrapper<MerchantArea> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(MerchantArea::getMerchantId, ids)
                .eq(MerchantArea::getIsDelete, 0);
        List<MerchantArea> downstreamAreas = merchantAreaMapper.selectList(wrapper);

        tableData.getTable().forEach(downstreamDTO -> {
            downstreamDTO.setBusinessArea(downstreamAreas.stream()
                    .filter(ua -> ua.getMerchantId().equals(downstreamDTO.getId()))
                    .map(MerchantArea::getAreaCode)
                    .toList());
        });

        return new ResponseResult(200, tableData);
    }

    @Override
    public List<DownstreamExcelDTO> getExcel(DownstreamSearchDTO params) {
        List<DownstreamDTO> downstreamDTOList = merchantMapper.selectByDownstreamSearchDTO(params);

        List<Long> ids = downstreamDTOList.stream().map(DownstreamDTO::getId).toList();
        LambdaQueryWrapper<MerchantArea> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(MerchantArea::getMerchantId, ids)
                .eq(MerchantArea::getIsDelete, 0);
        List<MerchantArea> downstreamAreas = merchantAreaMapper.selectList(wrapper);

        downstreamDTOList.forEach(downstreamDTO -> {
            downstreamDTO.setBusinessArea(downstreamAreas.stream()
                    .filter(ua -> ua.getMerchantId().equals(downstreamDTO.getId()))
                    .map(MerchantArea::getAreaCode)
                    .toList());
        });

        return downstreamDTOList.stream()
                .map(DownstreamExcelDTO::new)
                .toList();
    }

    @Override
    @Transactional
    public ResponseResult insert(DownstreamDTO params) {
        String code = SystemCommonUtil.buildCode();
        Long userId = SystemCommonUtil.getNowUserId();

        Merchant downstream = new Merchant(params);
        downstream.setUpdateBy(userId);
        downstream.setCode(code);
        int x = merchantMapper.insert(downstream);

        if (params.getBusinessArea() != null && !params.getBusinessArea().isEmpty()) {
            List<MerchantArea> downstreamAreas = params.getBusinessArea().stream()
                    .map(area -> {
                        MerchantArea downstreamArea = new MerchantArea();
                        downstreamArea.setAreaCode(area);
                        downstreamArea.setMerchantId(downstream.getId());
                        downstreamArea.setUpdateBy(SystemCommonUtil.getNowUserId());
                        downstreamArea.setIsDelete(0);
                        return downstreamArea;
                    })
                    .toList();
            merchantAreaMapper.insertBatchSomeColumn(downstreamAreas);
        }

        if(params.getUser() != null){
            MerchantUserDTO user = new MerchantUserDTO(params.getUser());
            user.setMerchantId(downstream.getId());
            user.setRoleId(3L);  // 联系人的id为3
            ResponseResult result = userService.insert(user);
            if(result.getCode() != 200){
                throw  new BusinessException(400, "添加失败，因为" + result.getMsg());
            }
        }

        return new ResponseResult(200, "已插入" + x + "条数据");
    }

    @Override
    public ResponseResult update(DownstreamDTO params) {
        Merchant downstream = new Merchant(params);
        downstream.setUpdateBy(SystemCommonUtil.getNowUserId());
        int x = merchantMapper.updateById(downstream);

        if (params.getBusinessArea() != null && !params.getBusinessArea().isEmpty()) {
            LambdaUpdateWrapper<MerchantArea> areaWrapper = new LambdaUpdateWrapper<>();
            areaWrapper.eq(MerchantArea::getMerchantId, downstream.getId());
            areaWrapper.eq(MerchantArea::getIsDelete, 0);
            areaWrapper.set(MerchantArea::getUpdateBy, SystemCommonUtil.getNowUserId());
            areaWrapper.set(MerchantArea::getIsDelete, 1);
            merchantAreaMapper.update(null, areaWrapper);

            List<MerchantArea> upstreamAreas = params.getBusinessArea().stream()
                    .map(area -> {
                        MerchantArea areas = new MerchantArea();
                        areas.setAreaCode(area);
                        areas.setMerchantId(downstream.getId());
                        areas.setUpdateBy(SystemCommonUtil.getNowUserId());
                        areas.setIsDelete(0);
                        return areas;
                    })
                    .toList();
            merchantAreaMapper.insertBatchSomeColumn(upstreamAreas);
        }

        return new ResponseResult(200, "已更新" + x + "条数据");
    }

    @Override
    public ResponseResult delete(Long id) {
        Merchant params = new Merchant();
        params.setId(id);
        params.setUpdateBy(SystemCommonUtil.getNowUserId());
        params.setIsDelete(1);
        int x = merchantMapper.updateById(params);

        LambdaUpdateWrapper<MerchantArea> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MerchantArea::getMerchantId, id);
        wrapper.eq(MerchantArea::getIsDelete, 0);
        wrapper.set(MerchantArea::getUpdateBy, SystemCommonUtil.getNowUserId());
        wrapper.set(MerchantArea::getIsDelete, 1);
        merchantAreaMapper.update(null, wrapper);

        userService.deleteByMerchantId(id);

        return new ResponseResult(200, "已删除" + x + "条数据");

    }

    @Override
    public ResponseResult selectById(Long id) {
        LambdaQueryWrapper<Merchant> merchantWrapper = new LambdaQueryWrapper<>();
        merchantWrapper.eq(Merchant::getId, id);
        merchantWrapper.eq(Merchant::getIsDelete, 0);
        Merchant downstream = merchantMapper.selectOne(merchantWrapper);
        if (downstream == null) {
            return new ResponseResult(404, "无资源");
        }

        DownstreamDTO downstreamDTO = new DownstreamDTO(downstream);

        LambdaQueryWrapper<MerchantArea> areaWrapper = new LambdaQueryWrapper<>();
        areaWrapper.eq(MerchantArea::getMerchantId, downstreamDTO.getId());
        areaWrapper.eq(MerchantArea::getIsDelete, 0);
        List<MerchantArea> downstreamAreas = merchantAreaMapper.selectList(areaWrapper);
        List<String> businessArea = downstreamAreas.stream().map(MerchantArea::getAreaCode).toList();
        downstreamDTO.setBusinessArea(businessArea);
        return new ResponseResult(200, downstreamDTO);
    }

    @Override
    public ResponseResult selectOptions(String blurParam) {
        if (blurParam == null || blurParam.isEmpty()) {
            return new ResponseResult(200, "不能进行全表查询", new ArrayList<>());
        }
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<>();
        //  更改逻辑，查询所有商家
        wrapper.in(Merchant::getType, List.of("车商店铺", "汽修厂", "代理人"));
        wrapper.eq(Merchant::getIsDelete, 0);
        wrapper.and(w -> {
           w.like(Merchant::getName, blurParam)
           .or()
           .like(Merchant::getCode, blurParam);
        });
        List<Merchant> downstreamList = merchantMapper.selectList(wrapper);
        return new ResponseResult(200, downstreamList);
    }
}
