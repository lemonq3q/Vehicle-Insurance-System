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
import com.example.insurancesystem.mapper.RoleMapper;
import com.example.insurancesystem.mapper.UserMapper;
import com.example.insurancesystem.mapper.UserRoleMapper;
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

    private final String uniqueRoleName = "联系人";

    private final String DEFAULT_PASSWORD = "qwer1234";

    @Override
    public ResponseResult select(MerchantUserSearchDTO params) {
        PageHelper.startPage(params.getPageNum(), params.getPageSize());
        List<MerchantUserDTO> merchantUserDTOList = userMapper.selectByMerchantUserSearchDTO(params);
        TableData<MerchantUserDTO> tableData = new TableData<>(merchantUserDTOList);
        return new ResponseResult<>(200, tableData);
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
        List<User> userList = userMapper.selectUserByMerchantId(merchantId);
        userList = userList.stream()
                .sorted(Comparator.comparingInt(user -> {
                    // 定义优先级数字，越小越靠前
                    return switch (user.getRoleName()) {
                        case "联系人" -> 0;
                        case "收款人" -> 1;
                        default -> 99; // 其他角色排最后
                    };
                }))
                .toList();
        List<MerchantUserDTO> merchantUserDTOList = userList.stream()
                .map(MerchantUserDTO::new)
                .toList();
        return new ResponseResult(200, merchantUserDTOList);
    }

    @Override
    public ResponseResult selectUserOptions(String blurParam) {
        if (blurParam == null || blurParam.isEmpty()) {
            return new ResponseResult(200, "不能进行全表查询", new ArrayList<>());
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getIsDelete, 0);
        wrapper.and(w -> {
            w.like(User::getName, blurParam)
                    .or()
                    .like(User::getUsername, blurParam);
        });
        List<User> userList = userMapper.selectList(wrapper);
        return new ResponseResult(200,  userList);
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
        user.setIsApproval(0);
        int x = userMapper.insert(user);

        UserRole userRole = new UserRole();
        userRole.setRoleId(roleId);
        userRole.setUserId(user.getId());
        userRoleMapper.insert(userRole);

        return new ResponseResult(200, "注册成功，等待管理员审核");
    }

    @Override
    public ResponseResult approvalUser(Long id) {
        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(User::getId, id);
        wrapper.eq(User::getIsDelete, 0);
        wrapper.set(User::getIsApproval, 1);
        wrapper.set(User::getUpdateBy, SystemCommonUtil.getNowUserId());
        int x = userMapper.update(null, wrapper);
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
        if (params.getRoleId() != null){
            LambdaQueryWrapper<Role> roleWrapper = new LambdaQueryWrapper<>();
            roleWrapper.eq(Role::getId, params.getRoleId());
            roleWrapper.eq(Role::getIsDelete, 0);
            Role role = roleMapper.selectOne(roleWrapper);
            if (role == null){
                return new ResponseResult(400, "角色不存在");
            }
            if (role.getName().equals(uniqueRoleName)){
                Long count = userMapper.selectUserByRoleAndMerchantId(role.getId(), params.getMerchantId());
                if (count > 0){
                    return new ResponseResult(400, "联系人重复");
                }
            }
        }
        String defaultPassword = passwordEncoder.encode(DEFAULT_PASSWORD);
        User user = new User(params);
        user.setPassword(passwordEncoder.encode(defaultPassword));
        user.setUpdateBy(SystemCommonUtil.getNowUserId());
        int x = userMapper.insert(user);

        if (params.getRoleId() != null){
            UserRole userRole = new UserRole();
            userRole.setRoleId(params.getRoleId());
            userRole.setUserId(user.getId());
            userRole.setUpdateBy(SystemCommonUtil.getNowUserId());
            userRoleMapper.insert(userRole);
        }
        return new ResponseResult(200, "插入成功");
    }



    @Override
    public ResponseResult update(MerchantUserDTO params) {
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
        if (params.getRoleId() != null){
            LambdaQueryWrapper<Role> roleWrapper = new LambdaQueryWrapper<>();
            roleWrapper.eq(Role::getId, params.getRoleId());
            roleWrapper.eq(Role::getIsDelete, 0);
            Role role = roleMapper.selectOne(roleWrapper);
            if (role == null){
                return new ResponseResult(400, "更新失败，角色不存在");
            }
            if (role.getName().equals(uniqueRoleName)){
                Long count = userMapper.selectUserByRoleAndMerchantId(role.getId(), params.getMerchantId());
                if (count > 0){
                    return new ResponseResult(400, "更新失败，联系人重复");
                }
            }
        }
        User user = new User(params);
        user.setUpdateBy(SystemCommonUtil.getNowUserId());
        int x = userMapper.updateById(user);

        if (params.getRoleId() != null){
            LambdaUpdateWrapper<UserRole> userRoleWrapper = new LambdaUpdateWrapper<>();
            userRoleWrapper.eq(UserRole::getUserId, user.getId());
            userRoleWrapper.eq(UserRole::getIsDelete, 0);
            userRoleWrapper.set(UserRole::getUpdateBy, SystemCommonUtil.getNowUserId());
            userRoleWrapper.set(UserRole::getIsDelete, 1);
            userRoleMapper.update(null, userRoleWrapper);

            UserRole userRole = new UserRole();
            userRole.setUserId(user.getId());
            userRole.setRoleId(params.getRoleId());
            userRole.setUpdateBy(SystemCommonUtil.getNowUserId());
            userRoleMapper.insert(userRole);
        }
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

        LambdaUpdateWrapper<UserRole> userRoleWrapper = new LambdaUpdateWrapper<>();
        userRoleWrapper.eq(UserRole::getUserId, id);
        userRoleWrapper.eq(UserRole::getIsDelete, 0);
        userRoleWrapper.set(UserRole::getUpdateBy, SystemCommonUtil.getNowUserId());
        userRoleWrapper.set(UserRole::getIsDelete, 1);
        userRoleMapper.update(null, userRoleWrapper);

        return new ResponseResult(200, "已删除" + x + "条数据");
    }

    @Override
    public ResponseResult deleteByMerchantId(Long merchantId) {
        Long nowUserId = SystemCommonUtil.getNowUserId();
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(User::getMerchantId, merchantId);
        userWrapper.eq(User::getIsDelete, 0);
        List<User> users = userMapper.selectList(userWrapper);
        List<Long> ids = users.stream().map(User::getId).toList();
        if(!ids.isEmpty()){
            LambdaUpdateWrapper<User> userUpdateWrapper = new LambdaUpdateWrapper<>();
            userUpdateWrapper.eq(User::getIsDelete, 0);
            userUpdateWrapper.in(User::getId, ids);
            userUpdateWrapper.set(User::getIsDelete, 1);
            userUpdateWrapper.set(User::getUpdateBy, nowUserId);
            userMapper.update(null, userUpdateWrapper);

            LambdaUpdateWrapper<UserRole> userRoleUpdateWrapper = new LambdaUpdateWrapper<>();
            userRoleUpdateWrapper.eq(UserRole::getIsDelete, 0);
            userRoleUpdateWrapper.in(UserRole::getUserId, ids);
            userRoleUpdateWrapper.set(UserRole::getIsDelete, 1);
            userRoleUpdateWrapper.set(UserRole::getUpdateBy, nowUserId);
            userRoleMapper.update(null, userRoleUpdateWrapper);
        }
        return new ResponseResult(200, "已删除" + users.size() + "条数据");
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

}
