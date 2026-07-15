package com.example.insurancesystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.insurancesystem.domain.authenticate.UserRole;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.authenticate.Role;
import com.example.insurancesystem.domain.encapsulate.TableData;
import com.example.insurancesystem.domain.merchant.Merchant;
import com.example.insurancesystem.domain.user.MerchantUserDTO;
import com.example.insurancesystem.domain.user.MerchantUserExcelDTO;
import com.example.insurancesystem.domain.user.MerchantUserSearchDTO;
import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.domain.user.TenantMember;
import com.example.insurancesystem.mapper.RoleMapper;
import com.example.insurancesystem.mapper.UserMapper;
import com.example.insurancesystem.mapper.UserRoleMapper;
import com.example.insurancesystem.mapper.TenantMemberMapper;
import com.example.insurancesystem.service.MerchantStaffService;
import com.example.insurancesystem.service.UserService;
import com.example.insurancesystem.utils.SystemCommonUtil;
import com.github.pagehelper.PageHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private TenantMemberMapper tenantMemberMapper;

    @Autowired
    private MerchantStaffService merchantStaffService;

    private final String uniqueRoleName = "联系人";

    private final String DEFAULT_PASSWORD = "qwer1234";

    @Override
    public ResponseResult select(MerchantUserSearchDTO params) {
        return merchantStaffService.select(params);
    }

    @Override
    public ResponseResult<User> selectByEmail(String email) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getEmail, email);
        wrapper.eq(User::getIsDelete, 0);
        User user = userMapper.selectOne(wrapper);
        if(user == null){
            return new ResponseResult<>(404, "用户不存在");
        }
        else{
            return new ResponseResult<>(200, user);
        }
    }

    @Override
    public List<MerchantUserExcelDTO> getExcel(MerchantUserSearchDTO params) {
        List<MerchantUserDTO> merchantUserDTOList = userMapper.selectByMerchantUserSearchDTO(params);
        return merchantUserDTOList.stream()
                .map(MerchantUserExcelDTO::new)
                .toList();
    }

    @Override
    public ResponseResult selectById(Long id) {
        MerchantUserDTO merchantUserDTO = userMapper.selectMerchantUserDTOById(id);
        if (merchantUserDTO == null){
            return new ResponseResult(404, "资源不存在");
        }
        return new ResponseResult(200, merchantUserDTO);
    }

    @Override
    public ResponseResult selectUserOptionsByMerchantId(Long merchantId) {
        return merchantStaffService.selectByMerchantId(merchantId);
    }

    @Override
    public ResponseResult selectUserOptions(String blurParam) {
        if (blurParam == null || blurParam.isEmpty()) {
            return new ResponseResult(200, "不能进行全表查询", new ArrayList<>());
        }
        MerchantUserSearchDTO search = new MerchantUserSearchDTO();
        search.setBlurParam(blurParam);
        search.setPageNum(1);
        search.setPageSize(20);
        return merchantStaffService.select(search);
    }

    @Override
    public ResponseResult updatePassword(User params) {
        Long userid = params.getId();
        if(userid == null){
            return new ResponseResult(400, "更新失败，用户不存在");
        }
        String password = params.getPassword();
        if (password == null || password.isEmpty() || password.length() < 6 || password.length() > 16){
            return new ResponseResult(400, "更新失败，违规密码");
        }
        password = passwordEncoder.encode(password);
        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(User::getId, userid);
        wrapper.eq(User::getIsDelete, 0);
        wrapper.set(User::getPassword, password);
        int x = userMapper.update(null, wrapper);
        if (x > 0){
            return new ResponseResult(200, "更新成功");
        }
        return new ResponseResult(400, "更新失败，用户不存在");
    }

    @Override
    public ResponseResult updatePasswordByEmail(String email, String password) {
        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(User::getEmail, email);
        wrapper.eq(User::getIsDelete, 0);
        wrapper.set(User::getPassword, passwordEncoder.encode(password));
        int x = userMapper.update(null, wrapper);
        if (x > 0){
            return new ResponseResult(200, "更新成功");
        }
        return new ResponseResult(400, "更新失败，用户不存在");
    }

    @Override
    public ResponseResult selectSystemUser(MerchantUserSearchDTO params) {
        PageHelper.startPage(params.getPageNum(), params.getPageSize());
        List<MerchantUserDTO> merchantUserDTOList = userMapper.selectSystemUserBySearchDTO(params);
        TableData<MerchantUserDTO> tableData = new TableData<>(merchantUserDTOList);
        return new ResponseResult<>(200, tableData);
    }

    @Override
    public ResponseResult selectNotApprovalUser(MerchantUserSearchDTO params) {
        PageHelper.startPage(params.getPageNum(), params.getPageSize());
        List<MerchantUserDTO> merchantUserDTOList = userMapper.selectNotApprovalUser(params);
        TableData<MerchantUserDTO> tableData = new TableData<>(merchantUserDTOList);
        return new ResponseResult<>(200, tableData);
    }

    @Override
    public ResponseResult registerPersonal(User user) {
        if (user.getUsername() != null){
            if (judgeRepeatPhone(user.getUsername(), -1L)){
                return new ResponseResult(400, "注册失败，手机号已注册");
            }
        }
        if(user.getEmail() != null){
            if (judgeRepeatEmail(user.getEmail(), -1L)){
                return new ResponseResult(400, "注册失败，邮箱已注册");
            }
        }
        long roleId;
        if(user.getRoleName() != null && !user.getRoleName().isEmpty() && user.getRoleName().equals("admin")){
            roleId = 1L;
        }
        else {
            roleId = 7L;
        }
        String password = passwordEncoder.encode(user.getPassword());
        user.setPassword(password);
        user.setStatus(1);
        user.setIsDelete(0);
        int x = userMapper.insert(user);
        insertMember(user.getId(), roleCode(roleId), 2);

        return new ResponseResult(200, "注册成功，等待管理员审核");
    }

    @Override
    public ResponseResult approvalUser(Long id) {
        LambdaUpdateWrapper<TenantMember> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(TenantMember::getUserId, id).eq(TenantMember::getIsDelete, 0)
                .set(TenantMember::getStatus, 1)
                .set(TenantMember::getUpdatedBy, SystemCommonUtil.getNowUserId());
        int x = tenantMemberMapper.update(null, wrapper);
        if (x > 0){
            return new ResponseResult(200, "审核成功");
        }
        return new ResponseResult(400, "审核失败，用户不存在");
    }

    @Override
    public ResponseResult selectSystemUserOptions(String blurParam) {
        if (blurParam == null || blurParam.isEmpty()) {
            return new ResponseResult(200, "不能进行全表查询", new ArrayList<>());
        }
        List<User> userList = userMapper.selectSystemUserOptions(blurParam);
        return new ResponseResult(200,  userList);
    }

    @Override
    public ResponseResult insert(MerchantUserDTO params) {
        if (params.getMerchantId() != null) {
            return merchantStaffService.insert(params);
        }
        if (params.getUsername() != null){
            if (judgeRepeatPhone(params.getUsername(), -1L)){
                return new ResponseResult(400, "手机号码已被注册");
            }
        }
        if(params.getEmail() != null){
            if (judgeRepeatEmail(params.getEmail(), -1L)){
                return new ResponseResult(400, "邮箱已被注册");
            }
        }
        Role role = findSystemRole(params.getRoleId());
        if (role == null) return new ResponseResult(400, "角色不存在");
        String defaultPassword = passwordEncoder.encode(DEFAULT_PASSWORD);
        User user = new User(params);
        user.setPassword(defaultPassword);
        user.setStatus(1);
        user.setIsDelete(0);
        user.setUpdateBy(SystemCommonUtil.getNowUserId());
        userMapper.insert(user);
        insertMember(user.getId(), roleCode(role), 1);
        return new ResponseResult(200, "插入成功");
    }



    @Override
    public ResponseResult update(MerchantUserDTO params) {
        if (params.getMerchantId() != null) {
            return merchantStaffService.update(params);
        }
        MerchantUserDTO current = params.getId() == null ? null : userMapper.selectMerchantUserDTOById(params.getId());
        if (current == null) return new ResponseResult(404, "更新失败，用户不存在");
        mergeMissing(params, current);
        if (params.getUsername() != null){
            if (judgeRepeatPhone(params.getUsername(), params.getId())){
                return new ResponseResult(400, "更新失败，手机号码重复");
            }
        }
        if(params.getEmail() != null){
            if (judgeRepeatEmail(params.getEmail(), params.getId())){
                return new ResponseResult(400, "更新失败，邮箱重复");
            }
        }
        Role role = findSystemRole(params.getRoleId());
        if (role == null) return new ResponseResult(400, "更新失败，角色不存在");
        User user = new User(params);
        user.setUpdateBy(SystemCommonUtil.getNowUserId());
        int x = userMapper.updateById(user);

        LambdaUpdateWrapper<TenantMember> member = new LambdaUpdateWrapper<>();
        member.eq(TenantMember::getUserId, user.getId()).eq(TenantMember::getIsDelete, 0)
                .set(TenantMember::getRoleCode, roleCode(role))
                .set(TenantMember::getUpdatedBy, SystemCommonUtil.getNowUserId());
        tenantMemberMapper.update(null, member);
        return new ResponseResult(200, "已更新" + x + "条数据");
    }

    @Override
    public ResponseResult delete(Long id) {
        LambdaUpdateWrapper<User> userWrapper = new LambdaUpdateWrapper<>();
        userWrapper.eq(User::getId, id);
        userWrapper.eq(User::getIsDelete, 0);
        userWrapper.set(User::getUpdateBy, SystemCommonUtil.getNowUserId());
        userWrapper.set(User::getIsDelete, 1);
        int x = userMapper.update(null, userWrapper);

        LambdaUpdateWrapper<TenantMember> memberWrapper = new LambdaUpdateWrapper<>();
        memberWrapper.eq(TenantMember::getUserId, id).eq(TenantMember::getIsDelete, 0)
                .set(TenantMember::getUpdatedBy, SystemCommonUtil.getNowUserId())
                .set(TenantMember::getIsDelete, 1);
        tenantMemberMapper.update(null, memberWrapper);

        return new ResponseResult(200, "已删除" + x + "条数据");
    }

    @Override
    public ResponseResult deleteByMerchantId(Long merchantId) {
        return merchantStaffService.deleteByMerchantId(merchantId);
    }

    private boolean judgeRepeatPhone(String phone, Long id) {
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(User::getUsername, phone);
        userWrapper.eq(User::getIsDelete, 0);
        userWrapper.ne(User::getId, id);
        User user = userMapper.selectOne(userWrapper);
        return user != null;
    }

    private boolean judgeRepeatEmail(String email, Long id){
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(User::getEmail, email);
        userWrapper.eq(User::getIsDelete, 0);
        userWrapper.ne(User::getId, id);
        User user = userMapper.selectOne(userWrapper);
        return user != null;
    }

    private Role findSystemRole(Long roleId) {
        if (roleId == null) return null;
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getId, roleId).eq(Role::getIsDelete, 0);
        Role role = roleMapper.selectOne(wrapper);
        if (role == null || !("admin".equals(role.getName()) || "出单员".equals(role.getName()))) return null;
        return role;
    }

    private String roleCode(Role role) {
        return "admin".equals(role.getName()) ? "ADMIN" : "ISSUER";
    }

    private String roleCode(long legacyRoleId) {
        return legacyRoleId == 1L ? "ADMIN" : "ISSUER";
    }

    private void insertMember(Long userId, String roleCode, int status) {
        TenantMember member = new TenantMember();
        member.setEnterpriseId(1L);
        member.setUserId(userId);
        member.setRoleCode(roleCode);
        member.setStatus(status);
        member.setUpdatedBy(userId);
        member.setIsDelete(0);
        tenantMemberMapper.insert(member);
    }

    private void mergeMissing(MerchantUserDTO target, MerchantUserDTO current) {
        if (target.getName() == null) target.setName(current.getName());
        if (target.getUsername() == null) target.setUsername(current.getUsername());
        if (target.getEmail() == null) target.setEmail(current.getEmail());
        if (target.getIdNum() == null) target.setIdNum(current.getIdNum());
        if (target.getRoleId() == null) target.setRoleId(current.getRoleId());
        if (target.getStatus() == null) target.setStatus(current.getStatus());
    }

}
