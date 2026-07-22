package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.authenticate.Menu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MenuMapper extends BatchBaseMapper<Menu> {

    List<String> selectPermsByUserId(@Param("userId") Long userId,
                                     @Param("enterpriseId") Long enterpriseId);
}
