package com.example.insurancesystem.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.insurancesystem.domain.merchant.Merchant;
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.mapper.MerchantMapper;
import com.example.insurancesystem.security.EnterpriseContextHolder;
import org.springframework.stereotype.Service;

/**
 * 统一上下游名称的企业内唯一规则，供手工新增、编辑复用。
 * 使用与数据库相同的名称比较方式，并排除当前记录；数据库有效名称索引负责并发兜底。
 * 不维护跨请求状态，不创建或修改机构，校验应置于机构及区域事务写入之前。
 */
@Service
public class MerchantNameValidationService {
    private final MerchantMapper mapper;

    /**
     * 注入当前租户隔离下的主体访问器，不自行创建数据库连接。
     * @param mapper 现有商户 Mapper。
     */
    public MerchantNameValidationService(MerchantMapper mapper) { this.mapper = mapper; }

    /**
     * 规范名称并检查同企业同方向的有效主体，编辑时排除自身。
     * @param merchant 来自表单的主体，direction 由上下游服务赋值，不信任客户端。
     * @throws BusinessException 名称为空、超长或已被占用；失败时尚未写入任何业务数据。
     */
    public void validate(Merchant merchant) {
        String name = merchant.getName();
        if (name == null || name.trim().isEmpty() || name.trim().length() > 100) {
            throw new BusinessException(400, "机构或商家名称不能为空且最多100字");
        }
        merchant.setName(name.trim());
        LambdaQueryWrapper<Merchant> query = new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getEnterpriseId, EnterpriseContextHolder.requireEnterpriseId())
                .eq(Merchant::getDirection, merchant.getDirection())
                .eq(Merchant::getName, merchant.getName()).eq(Merchant::getIsDelete, 0);
        if (merchant.getId() != null) query.ne(Merchant::getId, merchant.getId());
        if (mapper.selectCount(query) > 0) {
            throw new BusinessException(400, "UPSTREAM".equals(merchant.getDirection())
                    ? "当前企业已存在同名上游机构" : "当前企业已存在同名下游");
        }
    }
}
