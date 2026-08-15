package com.example.insurancesystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.encapsulate.TableData;
import com.example.insurancesystem.domain.merchant.MerchantStaff;
import com.example.insurancesystem.domain.merchant.MerchantStaffRole;
import com.example.insurancesystem.domain.merchant.MerchantStaffRoleCode;
import com.example.insurancesystem.domain.merchant.MerchantStaffRoles;
import com.example.insurancesystem.domain.user.MerchantUserDTO;
import com.example.insurancesystem.domain.user.MerchantUserSearchDTO;
import com.example.insurancesystem.mapper.MerchantStaffMapper;
import com.example.insurancesystem.mapper.MerchantStaffRoleMapper;
import com.example.insurancesystem.service.MerchantStaffService;
import com.example.insurancesystem.security.EnterpriseContextHolder;
import com.example.insurancesystem.utils.SystemCommonUtil;
import com.github.pagehelper.PageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
/**
 * 管理商户联系人、收款人等员工资料以及员工在商户下承担的业务角色。
 * 服务同时维护“默认收款人”唯一性，确保删除或改角色后仍有可用的默认收款对象。
 */
public class MerchantStaffServiceImpl implements MerchantStaffService {
    private final MerchantStaffMapper staffMapper;
    private final MerchantStaffRoleMapper roleMapper;

    /**
     * 注入员工和角色两个数据访问组件，所有聚合写入通过事务保持一致。
     */
    public MerchantStaffServiceImpl(MerchantStaffMapper staffMapper, MerchantStaffRoleMapper roleMapper) {
        this.staffMapper = staffMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    /**
     * 按商户、角色及关键字等条件分页查询员工聚合信息。
     */
    public ResponseResult select(MerchantUserSearchDTO params) {
        PageHelper.startPage(params.getPageNum(), params.getPageSize());
        return new ResponseResult<>(200, new TableData<>(staffMapper.selectStaff(params)));
    }

    @Override
    /**
     * 查询员工及其当前有效角色，不存在时返回资源缺失响应。
     */
    public ResponseResult selectById(Long id) {
        MerchantUserDTO staff = staffMapper.selectStaffById(id);
        return staff == null ? new ResponseResult(404, "资源不存在") : new ResponseResult(200, staff);
    }

    @Override
    /**
     * 返回指定商户下可供业务表单选择的员工列表。
     */
    public ResponseResult selectByMerchantId(Long merchantId) {
        return new ResponseResult(200, staffMapper.selectOptionsByMerchantId(merchantId));
    }

    @Override
    @Transactional
    /**
     * 创建商户员工及角色关系。联系人角色在同一商户内只允许一人，
     * 首位收款人会自动成为默认收款人。
     */
    public ResponseResult insert(MerchantUserDTO params) {
        String roleCode = MerchantStaffRoles.codeOf(params.getRoleId());
        ResponseResult validation = validate(params, roleCode, null);
        if (validation != null) return validation;

        MerchantStaff staff = toEntity(params);
        staffMapper.insert(staff);
        roleMapper.insert(newRole(staff, roleCode));
        return new ResponseResult(200, "插入成功");
    }

    @Override
    @Transactional
    /**
     * 更新员工资料和当前角色。缺省字段沿用数据库原值；若默认收款人被改为其他角色，
     * 会自动选择最早的有效收款人接替默认身份。
     */
    public ResponseResult update(MerchantUserDTO params) {
        MerchantUserDTO current = params.getId() == null ? null : staffMapper.selectStaffById(params.getId());
        if (current == null) {
            return new ResponseResult(404, "资源不存在");
        }
        mergeMissing(params, current);
        String roleCode = MerchantStaffRoles.codeOf(params.getRoleId());
        ResponseResult validation = validate(params, roleCode, params.getId());
        if (validation != null) return validation;

        MerchantStaff staff = toEntity(params);
        staffMapper.updateById(staff);
        MerchantStaffRole activeRole = roleMapper.selectOne(new LambdaQueryWrapper<MerchantStaffRole>()
                .eq(MerchantStaffRole::getStaffId, staff.getId())
                .eq(MerchantStaffRole::getIsDelete, 0));
        MerchantStaffRole newRole = newRole(staff, roleCode);
        if (activeRole == null) {
            roleMapper.insert(newRole);
        } else {
            boolean removedDefaultPayee = MerchantStaffRoleCode.PAYEE.equals(activeRole.getRoleCode())
                    && Integer.valueOf(1).equals(activeRole.getIsDefault())
                    && !MerchantStaffRoleCode.PAYEE.equals(newRole.getRoleCode());
            if (MerchantStaffRoleCode.PAYEE.equals(activeRole.getRoleCode())
                    && MerchantStaffRoleCode.PAYEE.equals(newRole.getRoleCode())) {
                newRole.setIsDefault(activeRole.getIsDefault());
            }
            activeRole.setRoleCode(newRole.getRoleCode());
            activeRole.setIsDefault(newRole.getIsDefault());
            activeRole.setUpdatedBy(newRole.getUpdatedBy());
            roleMapper.updateById(activeRole);
            if (removedDefaultPayee) promoteDefaultPayee(staff.getMerchantId());
        }
        return new ResponseResult(200, "已更新1条数据");
    }

    @Override
    @Transactional
    /**
     * 逻辑删除员工及其有效角色；删除默认收款人后同步补选新的默认收款人。
     * 重复删除按幂等成功处理。
     */
    public ResponseResult delete(Long id) {
        MerchantStaff staff = staffMapper.selectById(id);
        if (staff == null || Integer.valueOf(1).equals(staff.getIsDelete())) {
            return new ResponseResult(200, "已删除0条数据");
        }
        Long operator = SystemCommonUtil.getNowUserId();
        MerchantStaffRole activeRole = roleMapper.selectOne(new LambdaQueryWrapper<MerchantStaffRole>()
                .eq(MerchantStaffRole::getStaffId, id).eq(MerchantStaffRole::getIsDelete, 0));
        staff.setIsDelete(1);
        staff.setUpdatedBy(operator);
        staffMapper.updateById(staff);
        LambdaUpdateWrapper<MerchantStaffRole> role = new LambdaUpdateWrapper<>();
        role.eq(MerchantStaffRole::getStaffId, id).eq(MerchantStaffRole::getIsDelete, 0)
                .set(MerchantStaffRole::getIsDelete, 1).set(MerchantStaffRole::getUpdatedBy, operator);
        roleMapper.update(null, role);
        if (activeRole != null && MerchantStaffRoleCode.PAYEE.equals(activeRole.getRoleCode())
                && Integer.valueOf(1).equals(activeRole.getIsDefault())) {
            promoteDefaultPayee(staff.getMerchantId());
        }
        return new ResponseResult(200, "已删除1条数据");
    }

    @Override
    @Transactional
    /**
     * 删除指定商户下的全部员工，复用单员工删除流程以保证角色和默认收款人规则一致。
     */
    public ResponseResult deleteByMerchantId(Long merchantId) {
        List<MerchantUserDTO> rows = staffMapper.selectOptionsByMerchantId(merchantId);
        for (MerchantUserDTO row : rows) delete(row.getId());
        return new ResponseResult(200, "已删除" + rows.size() + "条数据");
    }

    /**
     * 校验商户归属、人员名称以及联系人角色的商户内唯一性。
     * 更新时通过 excludeId 排除当前员工，避免把自身判定为重复记录。
     */
    private ResponseResult validate(MerchantUserDTO params, String roleCode, Long excludeId) {
        if (params.getMerchantId() == null) return new ResponseResult(400, "请选择所属商家");
        if (params.getName() == null || params.getName().isBlank()) return new ResponseResult(400, "请输入人员名称");
        if (MerchantStaffRoleCode.CONTACT.equals(roleCode)
                && staffMapper.countRole(params.getMerchantId(), roleCode, excludeId) > 0) {
            return new ResponseResult(400, "联系人重复");
        }
        return null;
    }

    /**
     * 将兼容旧用户结构的 DTO 转换为新的商户员工实体，并写入当前企业和审计信息。
     */
    private MerchantStaff toEntity(MerchantUserDTO params) {
        MerchantStaff staff = new MerchantStaff();
        staff.setId(params.getId());
        staff.setEnterpriseId(EnterpriseContextHolder.requireEnterpriseId());
        staff.setMerchantId(params.getMerchantId());
        staff.setName(params.getName());
        staff.setPhone(params.getUsername());
        staff.setEmail(params.getEmail());
        staff.setIdNum(params.getIdNum());
        staff.setStatus(params.getStatus() == null ? 1 : params.getStatus());
        staff.setUpdatedBy(SystemCommonUtil.getNowUserId());
        staff.setIsDelete(0);
        return staff;
    }

    /**
     * 为局部更新补齐未提交字段，避免 MyBatis 更新时意外清空员工已有资料或角色。
     */
    private void mergeMissing(MerchantUserDTO target, MerchantUserDTO current) {
        if (target.getName() == null) target.setName(current.getName());
        if (target.getUsername() == null) target.setUsername(current.getUsername());
        if (target.getEmail() == null) target.setEmail(current.getEmail());
        if (target.getIdNum() == null) target.setIdNum(current.getIdNum());
        if (target.getMerchantId() == null) target.setMerchantId(current.getMerchantId());
        if (target.getRoleId() == null) target.setRoleId(current.getRoleId());
        if (target.getStatus() == null) target.setStatus(current.getStatus());
    }

    /**
     * 构建员工角色关系。创建收款人时，仅当商户尚无其他有效收款人，才将其标记为默认。
     */
    private MerchantStaffRole newRole(MerchantStaff staff, String roleCode) {
        MerchantStaffRole role = new MerchantStaffRole();
        role.setEnterpriseId(EnterpriseContextHolder.requireEnterpriseId());
        role.setMerchantId(staff.getMerchantId());
        role.setStaffId(staff.getId());
        role.setRoleCode(roleCode);
        role.setIsDefault(MerchantStaffRoleCode.PAYEE.equals(roleCode)
                && staffMapper.countRole(staff.getMerchantId(), roleCode, staff.getId()) == 0 ? 1 : 0);
        role.setUpdatedBy(SystemCommonUtil.getNowUserId());
        role.setIsDelete(0);
        return role;
    }

    /**
     * 在默认收款人离开后，按角色记录顺序提升最早的有效收款人，维持商户付款配置可用。
     */
    private void promoteDefaultPayee(Long merchantId) {
        MerchantStaffRole next = roleMapper.selectOne(new LambdaQueryWrapper<MerchantStaffRole>()
                .eq(MerchantStaffRole::getMerchantId, merchantId)
                .eq(MerchantStaffRole::getRoleCode, MerchantStaffRoleCode.PAYEE)
                .eq(MerchantStaffRole::getIsDelete, 0)
                .orderByAsc(MerchantStaffRole::getId)
                .last("LIMIT 1"));
        if (next != null) {
            next.setIsDefault(1);
            next.setUpdatedBy(SystemCommonUtil.getNowUserId());
            roleMapper.updateById(next);
        }
    }
}
