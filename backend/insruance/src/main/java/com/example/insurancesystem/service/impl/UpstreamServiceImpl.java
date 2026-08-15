package com.example.insurancesystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.encapsulate.TableData;
import com.example.insurancesystem.domain.merchant.*;
import com.example.insurancesystem.mapper.MerchantAreaMapper;
import com.example.insurancesystem.mapper.MerchantMapper;
import com.example.insurancesystem.service.UpstreamService;
import com.example.insurancesystem.security.EnterpriseContextHolder;
import com.example.insurancesystem.utils.SystemCommonUtil;
import com.example.insurancesystem.utils.UniqueCodeRetryUtil;
import com.github.pagehelper.PageHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


@Service
/**
 * 管理当前企业对接的上游保险机构及其承保区域。
 * 列表、详情和导出均以商户主体为基础，再聚合有效的区域关系。
 */
public class UpstreamServiceImpl implements UpstreamService {

    @Autowired
    private MerchantMapper upstreamMapper;

    @Autowired
    private MerchantAreaMapper upstreamAreaMapper;

    @Override
    /**
     * 分页查询保险机构，并批量补齐当前页机构的有效承保区域。
     */
    public ResponseResult select(UpstreamSearchDTO params) {
        PageHelper.startPage(params.getPageNum(), params.getPageSize());
        List<UpstreamDTO> upstreams = upstreamMapper.selectByUpstreamSearchDTO(params);
        TableData<UpstreamDTO> tableData = new TableData<>(upstreams);

        if (upstreams == null || upstreams.isEmpty()) {
            return new ResponseResult(200, tableData);
        }

        List<Long> ids = tableData.getTable().stream().map(UpstreamDTO::getId).toList();
        LambdaQueryWrapper<MerchantArea> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(MerchantArea::getMerchantId, ids)
                .eq(MerchantArea::getIsDelete, 0);
        List<MerchantArea> upstreamAreas = upstreamAreaMapper.selectList(wrapper);

        tableData.getTable().forEach(upstreamDTO -> {
            upstreamDTO.setBusinessArea(upstreamAreas.stream()
                    .filter(ua -> ua.getMerchantId().equals(upstreamDTO.getId()))
                    .map(MerchantArea::getAreaCode)
                    .toList());
        });

        return new ResponseResult(200, tableData);
    }

    @Override
    /**
     * 导出符合条件的全部保险机构，在转换 Excel 模型前补充承保区域。
     */
    public List<UpstreamExcelDTO> getExcel(UpstreamSearchDTO params) {
        List<UpstreamDTO> upstreams = upstreamMapper.selectByUpstreamSearchDTO(params);

        List<Long> ids = upstreams.stream().map(UpstreamDTO::getId).toList();
        LambdaQueryWrapper<MerchantArea> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(MerchantArea::getMerchantId, ids)
                .eq(MerchantArea::getIsDelete, 0);
        List<MerchantArea> upstreamAreas = upstreamAreaMapper.selectList(wrapper);

        upstreams.forEach(upstreamDTO -> {
            upstreamDTO.setBusinessArea(upstreamAreas.stream()
                    .filter(ua -> ua.getMerchantId().equals(upstreamDTO.getId()))
                    .map(MerchantArea::getAreaCode)
                    .toList());
        });

        return upstreams.stream().map(UpstreamExcelDTO::new).toList();
    }


    @Override
    /**
     * 查询单个保险机构详情及其有效承保区域，不存在时返回资源缺失响应。
     */
    public ResponseResult selectById(Long id) {
        UpstreamSearchDTO search = new UpstreamSearchDTO();
        search.setId(id);
        List<UpstreamDTO> upstreams = upstreamMapper.selectByUpstreamSearchDTO(search);
        if (upstreams == null || upstreams.isEmpty()) {
            return new ResponseResult(404, "无资源");
        }

        UpstreamDTO upstreamDTO = upstreams.get(0);
        LambdaQueryWrapper<MerchantArea> areaWrapper = new LambdaQueryWrapper<>();
        areaWrapper.eq(MerchantArea::getMerchantId, upstreamDTO.getId());
        areaWrapper.eq(MerchantArea::getIsDelete, 0);
        List<MerchantArea> upstreamAreas = upstreamAreaMapper.selectList(areaWrapper);
        List<String> businessArea = upstreamAreas.stream().map(MerchantArea::getAreaCode).toList();
        upstreamDTO.setBusinessArea(businessArea);
        return new ResponseResult(200, upstreamDTO);
    }

    @Override
    /**
     * 按名称或编码模糊检索保险公司选项；空关键字不执行全表查询。
     */
    public ResponseResult selectInsuranceCompanyOptions(String blurParam) {
        if (blurParam == null || blurParam.isEmpty()) {
            return new ResponseResult(200, "不能进行全表查询", new ArrayList<>());
        }
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Merchant::getCategoryId, upstreamMapper.selectCategoryIdByCode(MerchantCategoryCode.INSURANCE_ORG));
        wrapper.eq(Merchant::getIsDelete, 0);
        wrapper.and(w -> {
            w.like(Merchant::getName, blurParam)
                    .or()
                    .like(Merchant::getCode, blurParam);
        });
        List<Merchant> insuranceCompanyList = upstreamMapper.selectList(wrapper);
        return new ResponseResult(200, insuranceCompanyList);
    }

    @Override
    /**
     * 提供通用上游机构下拉选项，目前业务范围限定为保险机构类别。
     */
    public ResponseResult selectOptions(String blurParam) {
        if (blurParam == null || blurParam.isEmpty()) {
            return new ResponseResult(200, "不能进行全表查询", new ArrayList<>());
        }
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Merchant::getCategoryId, upstreamMapper.selectCategoryIdByCode(MerchantCategoryCode.INSURANCE_ORG));
        wrapper.eq(Merchant::getIsDelete, 0);
        wrapper.and(w -> {
            w.like(Merchant::getName, blurParam)
                    .or()
                    .like(Merchant::getCode, blurParam);
        });
        List<Merchant> downstreamList = upstreamMapper.selectList(wrapper);
        return new ResponseResult(200, downstreamList);
    }

    @Override
    /**
     * 创建保险机构及其承保区域。机构编码由唯一约束重试机制生成，所有记录绑定当前企业和操作人。
     */
    public ResponseResult insert(UpstreamDTO params) {
        Long userId = SystemCommonUtil.getNowUserId();

        Merchant upstream = new Merchant(params);
        upstream.setEnterpriseId(EnterpriseContextHolder.requireEnterpriseId());
        upstream.setCategoryId(upstreamMapper.selectCategoryIdByCode(MerchantCategoryCode.INSURANCE_ORG));
        upstream.setUpdateBy(userId);
        int x = UniqueCodeRetryUtil.insertWithGeneratedCode(
                UniqueCodeRetryUtil.MERCHANT_CODE_CONSTRAINT,
                upstream::setCode,
                () -> upstreamMapper.insert(upstream));

        if (params.getBusinessArea() != null && !params.getBusinessArea().isEmpty()) {
            List<MerchantArea> upstreamAreas = params.getBusinessArea().stream()
                    .map(area -> {
                        MerchantArea upstreamArea = new MerchantArea();
                        upstreamArea.setAreaCode(area);
                        upstreamArea.setMerchantId(upstream.getId());
                        upstreamArea.setEnterpriseId(EnterpriseContextHolder.requireEnterpriseId());
                        upstreamArea.setUpdateBy(SystemCommonUtil.getNowUserId());
                        upstreamArea.setIsDelete(0);
                        return upstreamArea;
                    })
                    .toList();
            upstreamAreaMapper.insertBatchSomeColumn(upstreamAreas);
        }

        return new ResponseResult(200, "已插入" + x + "条数据");
    }

    /**
     * 更新数据：params 中非 null 字段作为更新值，需保证 params 中有唯一标识（如 id）
     */
    @Override
    /**
     * 更新保险机构资料。提交了承保区域时，以逻辑删除旧关系、插入新关系的方式替换区域快照；
     * 未提交区域字段时只更新机构主体，保留现有承保范围。
     */
    public ResponseResult update(UpstreamDTO params) {
        Merchant upstream = new Merchant(params);
        upstream.setCategoryId(upstreamMapper.selectCategoryIdByCode(MerchantCategoryCode.INSURANCE_ORG));
        upstream.setUpdateBy(SystemCommonUtil.getNowUserId());
        int x = upstreamMapper.updateById(upstream);

        if (params.getBusinessArea() != null && !params.getBusinessArea().isEmpty()) {
            LambdaUpdateWrapper<MerchantArea> areaWrapper = new LambdaUpdateWrapper<>();
            areaWrapper.eq(MerchantArea::getMerchantId, upstream.getId());
            areaWrapper.eq(MerchantArea::getIsDelete, 0);
            areaWrapper.set(MerchantArea::getUpdateBy, SystemCommonUtil.getNowUserId());
            areaWrapper.set(MerchantArea::getIsDelete, 1);
            upstreamAreaMapper.update(null, areaWrapper);

            List<MerchantArea> upstreamAreas = params.getBusinessArea().stream()
                    .map(area -> {
                        MerchantArea upstreamArea = new MerchantArea();
                        upstreamArea.setAreaCode(area);
                        upstreamArea.setMerchantId(upstream.getId());
                        upstreamArea.setEnterpriseId(EnterpriseContextHolder.requireEnterpriseId());
                        upstreamArea.setUpdateBy(SystemCommonUtil.getNowUserId());
                        upstreamArea.setIsDelete(0);
                        return upstreamArea;
                    })
                    .toList();
            upstreamAreaMapper.insertBatchSomeColumn(upstreamAreas);
        }
        return new ResponseResult(200, "已更新" + x + "条数据");
    }

    /**
     * 根据 id 删除数据
     */
    @Override
    /**
     * 逻辑删除保险机构及其全部有效区域关系，保留历史工单引用所需的数据记录。
     */
    public ResponseResult delete(long id) {
        Merchant params = new Merchant();
        params.setId(id);
        params.setUpdateBy(SystemCommonUtil.getNowUserId());
        params.setIsDelete(1);
        int x = upstreamMapper.updateById(params);

        LambdaUpdateWrapper<MerchantArea> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(MerchantArea::getMerchantId, id);
        wrapper.eq(MerchantArea::getIsDelete, 0);
        wrapper.set(MerchantArea::getUpdateBy, SystemCommonUtil.getNowUserId());
        wrapper.set(MerchantArea::getIsDelete, 1);
        upstreamAreaMapper.update(null, wrapper);

        return new ResponseResult(200, "已删除" + x + "条数据");
    }

}
