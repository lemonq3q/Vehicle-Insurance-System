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
import com.example.insurancesystem.security.EnterpriseContextHolder;
import com.example.insurancesystem.service.MerchantStaffService;
import com.example.insurancesystem.utils.SystemCommonUtil;
import com.example.insurancesystem.utils.UniqueCodeRetryUtil;
import com.github.pagehelper.PageHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
/**
 * 管理当前企业合作的下游渠道商，包括车商、维修厂和代理机构。
 * 除商户主体外，本服务还维护经营区域和联系人，使列表、详情及导出得到完整聚合数据。
 */
public class DownstreamServiceImp implements DownstreamService {

    @Autowired
    private MerchantMapper merchantMapper;

    @Autowired
    private MerchantAreaMapper merchantAreaMapper;

    @Autowired
    private MerchantStaffService merchantStaffService;

    @Override
    /**
     * 分页查询下游商户，并批量读取当前页商户的有效经营区域后回填，避免逐行访问数据库。
     */
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
    /**
     * 按查询条件导出下游商户。导出不分页，并在转换 Excel 行模型前补齐经营区域。
     */
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
    /**
     * 在同一事务中创建下游商户、经营区域和可选联系人。
     * 商户编码通过唯一约束冲突重试生成；任一联系人创建失败都会回滚整个聚合创建过程。
     */
    public ResponseResult insert(DownstreamDTO params) {
        Long userId = SystemCommonUtil.getNowUserId();

        Merchant downstream = new Merchant(params);
        downstream.setEnterpriseId(EnterpriseContextHolder.requireEnterpriseId());
        downstream.setCategoryId(resolveCategoryId(params.getType()));
        downstream.setUpdateBy(userId);
        int x = UniqueCodeRetryUtil.insertWithGeneratedCode(
                UniqueCodeRetryUtil.MERCHANT_CODE_CONSTRAINT,
                downstream::setCode,
                () -> merchantMapper.insert(downstream));

        if (params.getBusinessArea() != null && !params.getBusinessArea().isEmpty()) {
            List<MerchantArea> downstreamAreas = params.getBusinessArea().stream()
                    .map(area -> {
                        MerchantArea downstreamArea = new MerchantArea();
                        downstreamArea.setAreaCode(area);
                        downstreamArea.setMerchantId(downstream.getId());
                        downstreamArea.setEnterpriseId(EnterpriseContextHolder.requireEnterpriseId());
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
            user.setRoleId(MerchantStaffRoles.CONTACT_ID);
            ResponseResult result = merchantStaffService.insert(user);
            if(result.getCode() != 200){
                throw  new BusinessException(400, "添加失败，因为" + result.getMsg());
            }
        }

        return new ResponseResult(200, "已插入" + x + "条数据");
    }

    @Override
    /**
     * 更新下游商户资料。提交了经营区域时，先逻辑删除旧关系再写入新快照，
     * 未提交区域字段则保留原有区域配置不变。
     */
    public ResponseResult update(DownstreamDTO params) {
        Merchant downstream = new Merchant(params);
        if (params.getType() != null) {
            downstream.setCategoryId(resolveCategoryId(params.getType()));
        }
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
                        areas.setEnterpriseId(EnterpriseContextHolder.requireEnterpriseId());
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
    /**
     * 逻辑删除下游商户，并同步停用其经营区域和商户员工关系，防止已删除主体继续被业务选择。
     */
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

        merchantStaffService.deleteByMerchantId(id);

        return new ResponseResult(200, "已删除" + x + "条数据");

    }

    @Override
    /**
     * 查询单个下游商户的聚合详情，并补充全部有效经营区域；不存在时返回资源缺失响应。
     */
    public ResponseResult selectById(Long id) {
        DownstreamSearchDTO search = new DownstreamSearchDTO();
        search.setId(id);
        List<DownstreamDTO> matches = merchantMapper.selectByDownstreamSearchDTO(search);
        if (matches.isEmpty()) {
            return new ResponseResult(404, "无资源");
        }
        DownstreamDTO downstreamDTO = matches.get(0);

        LambdaQueryWrapper<MerchantArea> areaWrapper = new LambdaQueryWrapper<>();
        areaWrapper.eq(MerchantArea::getMerchantId, downstreamDTO.getId());
        areaWrapper.eq(MerchantArea::getIsDelete, 0);
        List<MerchantArea> downstreamAreas = merchantAreaMapper.selectList(areaWrapper);
        List<String> businessArea = downstreamAreas.stream().map(MerchantArea::getAreaCode).toList();
        downstreamDTO.setBusinessArea(businessArea);
        return new ResponseResult(200, downstreamDTO);
    }

    @Override
    /**
     * 按名称或编码模糊检索有效下游商户选项。
     * 为防止下拉框触发无条件全表扫描，空关键字直接返回空集合。
     */
    public ResponseResult selectOptions(String blurParam) {
        if (blurParam == null || blurParam.isEmpty()) {
            return new ResponseResult(200, "不能进行全表查询", new ArrayList<>());
        }
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Merchant::getCategoryId, List.of(
                merchantMapper.selectCategoryIdByCode(MerchantCategoryCode.DEALER_STORE),
                merchantMapper.selectCategoryIdByCode(MerchantCategoryCode.AUTO_REPAIR),
                merchantMapper.selectCategoryIdByCode(MerchantCategoryCode.AGENT)));
        wrapper.eq(Merchant::getIsDelete, 0);
        wrapper.and(w -> {
           w.like(Merchant::getName, blurParam)
           .or()
           .like(Merchant::getCode, blurParam);
        });
        List<Merchant> downstreamList = merchantMapper.selectList(wrapper);
        return new ResponseResult(200, downstreamList);
    }

    /**
     * 将历史中文商户类型转换为字典编码及数据库主键，兼容旧前端入参并统一新表关联方式。
     */
    private Long resolveCategoryId(String legacyType) {
        String code = MerchantCategoryCode.fromLegacyName(legacyType == null ? "车商店铺" : legacyType);
        Long id = merchantMapper.selectCategoryIdByCode(code);
        if (id == null) throw new BusinessException(500, "商户分类字典缺失: " + code);
        return id;
    }
}
