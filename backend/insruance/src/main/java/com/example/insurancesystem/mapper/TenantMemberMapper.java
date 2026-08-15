package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.user.TenantMember;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * 维护用户加入企业后的成员角色、启用状态及逻辑删除状态。
 */
public interface TenantMemberMapper extends BatchBaseMapper<TenantMember> {
}
