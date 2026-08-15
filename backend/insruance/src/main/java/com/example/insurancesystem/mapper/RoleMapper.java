package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.authenticate.Role;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 访问系统角色字典，供企业账号分配及授权关系解析使用。
 */
public interface RoleMapper extends BatchBaseMapper<Role> {
}
