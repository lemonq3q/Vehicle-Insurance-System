package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.authenticate.UserRole;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 访问历史用户角色关系，保留旧权限模型兼容所需的通用持久化能力。
 */
public interface UserRoleMapper extends BatchBaseMapper<UserRole> {
}
