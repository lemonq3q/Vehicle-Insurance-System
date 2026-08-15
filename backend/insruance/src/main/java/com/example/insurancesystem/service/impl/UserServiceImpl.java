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
import com.example.insurancesystem.security.EnterpriseContextHolder;
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
/**
 * 统一承接系统账号、企业成员和商户员工三类人员管理入口。
 * 带商户归属的请求委托给商户员工服务；企业内部账号则同步维护用户主体和租户成员角色。
 */
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
    /**
     * 查询商户员工列表，沿用独立员工服务中的角色与商户隔离规则。
     */
    public ResponseResult select(MerchantUserSearchDTO params) {
        return merchantStaffService.select(params);
    }

    @Override
    /**
     * 按邮箱查找未删除的系统账号，供找回密码流程确认账号归属。
     */
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
    /**
     * 查询符合条件的传统商户用户数据并转换为 Excel 导出模型。
     */
    public List<MerchantUserExcelDTO> getExcel(MerchantUserSearchDTO params) {
        List<MerchantUserDTO> merchantUserDTOList = userMapper.selectByMerchantUserSearchDTO(params);
        return merchantUserDTOList.stream()
                .map(MerchantUserExcelDTO::new)
                .toList();
    }

    @Override
    /**
     * 查询单个人员的聚合资料，包含其角色及关联业务信息。
     */
    public ResponseResult selectById(Long id) {
        MerchantUserDTO merchantUserDTO = userMapper.selectMerchantUserDTOById(id);
        if (merchantUserDTO == null){
            return new ResponseResult(404, "资源不存在");
        }
        return new ResponseResult(200, merchantUserDTO);
    }

    @Override
    /**
     * 返回指定商户下可供工单等业务表单选择的员工选项。
     */
    public ResponseResult selectUserOptionsByMerchantId(Long merchantId) {
        return merchantStaffService.selectByMerchantId(merchantId);
    }

    @Override
    /**
     * 按关键字查询最多一页商户员工选项；空关键字不允许触发全量人员查询。
     */
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
    /**
     * 按用户主键直接重置密码，校验密码长度后统一使用安全编码器落库。
     */
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
    /**
     * 邮箱验证码校验通过后按邮箱重置密码，只更新仍有效的账号。
     */
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
    /**
     * 分页查询当前企业已经纳入管理的系统账号及成员角色。
     */
    public ResponseResult selectSystemUser(MerchantUserSearchDTO params) {
        PageHelper.startPage(params.getPageNum(), params.getPageSize());
        List<MerchantUserDTO> merchantUserDTOList = userMapper.selectSystemUserBySearchDTO(params);
        TableData<MerchantUserDTO> tableData = new TableData<>(merchantUserDTOList);
        return new ResponseResult<>(200, tableData);
    }

    @Override
    /**
     * 分页查询自行注册但尚待企业管理员审核的成员申请。
     */
    public ResponseResult selectNotApprovalUser(MerchantUserSearchDTO params) {
        PageHelper.startPage(params.getPageNum(), params.getPageSize());
        List<MerchantUserDTO> merchantUserDTOList = userMapper.selectNotApprovalUser(params);
        TableData<MerchantUserDTO> tableData = new TableData<>(merchantUserDTOList);
        return new ResponseResult<>(200, tableData);
    }

    @Override
    /**
     * 创建个人注册账号并写入待审核企业成员关系。
     * 手机号、邮箱必须唯一，管理员与出单员分别映射到对应的租户角色编码。
     */
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
    /**
     * 将指定用户在当前企业下的有效成员关系改为启用状态，完成注册审核。
     */
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
    /**
     * 按关键字检索企业系统账号选项，空关键字直接返回空集合以避免全表查询。
     */
    public ResponseResult selectSystemUserOptions(String blurParam) {
        if (blurParam == null || blurParam.isEmpty()) {
            return new ResponseResult(200, "不能进行全表查询", new ArrayList<>());
        }
        List<User> userList = userMapper.selectSystemUserOptions(blurParam);
        return new ResponseResult(200,  userList);
    }

    @Override
    /**
     * 创建人员记录。带 merchantId 时创建商户员工；否则创建企业系统账号，
     * 使用初始密码并同步建立已启用的租户成员角色关系。
     */
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
    /**
     * 更新人员记录。商户员工交由专用服务处理；系统账号则校验手机和邮箱唯一性，
     * 更新用户主体后同步调整其企业成员角色编码。
     */
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
    /**
     * 逻辑删除系统账号及其全部有效企业成员关系，保留历史业务引用。
     */
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
    /**
     * 删除指定商户的全部员工，由商户员工服务维护角色清理和默认收款人规则。
     */
    public ResponseResult deleteByMerchantId(Long merchantId) {
        return merchantStaffService.deleteByMerchantId(merchantId);
    }

    /**
     * 判断手机号是否已被其他有效账号使用；id 用于更新场景排除当前账号。
     */
    private boolean judgeRepeatPhone(String phone, Long id) {
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(User::getUsername, phone);
        userWrapper.eq(User::getIsDelete, 0);
        userWrapper.ne(User::getId, id);
        User user = userMapper.selectOne(userWrapper);
        return user != null;
    }

    /**
     * 判断邮箱是否已被其他有效账号使用；id 用于更新场景排除当前账号。
     */
    private boolean judgeRepeatEmail(String email, Long id){
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(User::getEmail, email);
        userWrapper.eq(User::getIsDelete, 0);
        userWrapper.ne(User::getId, id);
        User user = userMapper.selectOne(userWrapper);
        return user != null;
    }

    /**
     * 读取并验证可分配给企业系统账号的角色，只接受管理员和出单员两类业务角色。
     */
    private Role findSystemRole(Long roleId) {
        if (roleId == null) return null;
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getId, roleId).eq(Role::getIsDelete, 0);
        Role role = roleMapper.selectOne(wrapper);
        if (role == null || !("admin".equals(role.getName()) || "出单员".equals(role.getName()))) return null;
        return role;
    }

    /**
     * 将旧角色实体映射为租户成员表使用的稳定角色编码。
     */
    private String roleCode(Role role) {
        return "admin".equals(role.getName()) ? "ADMIN" : "ISSUER";
    }

    /**
     * 将个人注册流程沿用的历史角色主键映射为租户角色编码。
     */
    private String roleCode(long legacyRoleId) {
        return legacyRoleId == 1L ? "ADMIN" : "ISSUER";
    }

    /**
     * 为用户建立当前企业成员关系，并记录角色、审核状态和审计字段。
     */
    private void insertMember(Long userId, String roleCode, int status) {
        TenantMember member = new TenantMember();
        member.setEnterpriseId(EnterpriseContextHolder.requireEnterpriseId());
        member.setUserId(userId);
        member.setRoleCode(roleCode);
        member.setStatus(status);
        member.setUpdatedBy(userId);
        member.setIsDelete(0);
        tenantMemberMapper.insert(member);
    }

    /**
     * 为局部更新补齐未提交字段，避免更新用户主体时清空已有资料或角色。
     */
    private void mergeMissing(MerchantUserDTO target, MerchantUserDTO current) {
        if (target.getName() == null) target.setName(current.getName());
        if (target.getUsername() == null) target.setUsername(current.getUsername());
        if (target.getEmail() == null) target.setEmail(current.getEmail());
        if (target.getIdNum() == null) target.setIdNum(current.getIdNum());
        if (target.getRoleId() == null) target.setRoleId(current.getRoleId());
        if (target.getStatus() == null) target.setStatus(current.getStatus());
    }

}
