package com.example.insurancesystem.service.impl;

import com.example.insurancesystem.domain.authenticate.Role;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.mapper.RoleMapper;
import com.example.insurancesystem.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
/**
 * 负责读取系统角色字典，为用户创建、编辑和权限配置页面提供角色选项。
 */
public class RoleServiceImpl implements RoleService {

    @Autowired
    private RoleMapper roleMapper;

    @Override
    /**
     * 返回数据库中定义的全部角色；角色可见范围由上层接口权限控制。
     *
     * @return 包含角色列表的统一响应
     */
    public ResponseResult selectAll() {
        List<Role> roleList = roleMapper.selectList(null);
        return new ResponseResult(200, roleList);
    }
}
