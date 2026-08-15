package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.authenticate.Menu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
/**
 * 访问菜单与权限配置，并按企业成员角色解析登录用户的授权标识。
 */
public interface MenuMapper extends BatchBaseMapper<Menu> {

    /**
     * 查询用户在指定企业下由成员角色继承的全部权限编码。
     */
    List<String> selectPermsByUserId(@Param("userId") Long userId,
                                     @Param("enterpriseId") Long enterpriseId);
}
