package com.example.insurancesystem.service.impl;

import com.example.insurancesystem.domain.authenticate.Role;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.mapper.RoleMapper;
import com.example.insurancesystem.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoleServiceImpl implements RoleService {

    @Autowired
    private RoleMapper roleMapper;

    @Override
    public ResponseResult selectAll() {
        List<Role> roleList = roleMapper.selectList(null);
        return new ResponseResult(200, roleList);
    }
}
