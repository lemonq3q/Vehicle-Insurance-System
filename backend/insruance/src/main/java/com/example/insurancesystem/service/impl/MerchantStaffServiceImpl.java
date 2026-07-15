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
import com.example.insurancesystem.utils.SystemCommonUtil;
import com.github.pagehelper.PageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MerchantStaffServiceImpl implements MerchantStaffService {
    private static final Long LEGACY_ENTERPRISE_ID = 1L;

    private final MerchantStaffMapper staffMapper;
    private final MerchantStaffRoleMapper roleMapper;

    public MerchantStaffServiceImpl(MerchantStaffMapper staffMapper, MerchantStaffRoleMapper roleMapper) {
        this.staffMapper = staffMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public ResponseResult select(MerchantUserSearchDTO params) {
        PageHelper.startPage(params.getPageNum(), params.getPageSize());
        return new ResponseResult<>(200, new TableData<>(staffMapper.selectStaff(params)));
    }

    @Override
    public ResponseResult selectById(Long id) {
        MerchantUserDTO staff = staffMapper.selectStaffById(id);
        return staff == null ? new ResponseResult(404, "资源不存在") : new ResponseResult(200, staff);
    }

    @Override
    public ResponseResult selectByMerchantId(Long merchantId) {
        return new ResponseResult(200, staffMapper.selectOptionsByMerchantId(merchantId));
    }

    @Override
    @Transactional
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
    public ResponseResult deleteByMerchantId(Long merchantId) {
        List<MerchantUserDTO> rows = staffMapper.selectOptionsByMerchantId(merchantId);
        for (MerchantUserDTO row : rows) delete(row.getId());
        return new ResponseResult(200, "已删除" + rows.size() + "条数据");
    }

    private ResponseResult validate(MerchantUserDTO params, String roleCode, Long excludeId) {
        if (params.getMerchantId() == null) return new ResponseResult(400, "请选择所属商家");
        if (params.getName() == null || params.getName().isBlank()) return new ResponseResult(400, "请输入人员名称");
        if (MerchantStaffRoleCode.CONTACT.equals(roleCode)
                && staffMapper.countRole(params.getMerchantId(), roleCode, excludeId) > 0) {
            return new ResponseResult(400, "联系人重复");
        }
        return null;
    }

    private MerchantStaff toEntity(MerchantUserDTO params) {
        MerchantStaff staff = new MerchantStaff();
        staff.setId(params.getId());
        staff.setEnterpriseId(LEGACY_ENTERPRISE_ID);
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

    private void mergeMissing(MerchantUserDTO target, MerchantUserDTO current) {
        if (target.getName() == null) target.setName(current.getName());
        if (target.getUsername() == null) target.setUsername(current.getUsername());
        if (target.getEmail() == null) target.setEmail(current.getEmail());
        if (target.getIdNum() == null) target.setIdNum(current.getIdNum());
        if (target.getMerchantId() == null) target.setMerchantId(current.getMerchantId());
        if (target.getRoleId() == null) target.setRoleId(current.getRoleId());
        if (target.getStatus() == null) target.setStatus(current.getStatus());
    }

    private MerchantStaffRole newRole(MerchantStaff staff, String roleCode) {
        MerchantStaffRole role = new MerchantStaffRole();
        role.setEnterpriseId(LEGACY_ENTERPRISE_ID);
        role.setMerchantId(staff.getMerchantId());
        role.setStaffId(staff.getId());
        role.setRoleCode(roleCode);
        role.setIsDefault(MerchantStaffRoleCode.PAYEE.equals(roleCode)
                && staffMapper.countRole(staff.getMerchantId(), roleCode, staff.getId()) == 0 ? 1 : 0);
        role.setUpdatedBy(SystemCommonUtil.getNowUserId());
        role.setIsDelete(0);
        return role;
    }

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
