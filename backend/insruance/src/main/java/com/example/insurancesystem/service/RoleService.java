package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;

/**
 * 提供车险系统授权角色字典。
 */
public interface RoleService {
    /**
     * 查询全部可分配角色。
     */
    ResponseResult selectAll();
}
