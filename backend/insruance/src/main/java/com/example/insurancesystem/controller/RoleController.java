package com.example.insurancesystem.controller;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/role")
/**
 * 暴露车险系统角色字典，供用户管理页面选择和展示系统授权角色。
 */
public class RoleController {

    @Autowired
    private RoleService roleService;

    @GetMapping
    /**
     * 返回全部可分配系统角色，筛选逻辑由 RoleService 负责。
     */
    public ResponseResult selectAll(){
        return roleService.selectAll();
    }
}
