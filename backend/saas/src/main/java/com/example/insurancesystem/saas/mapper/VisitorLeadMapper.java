package com.example.insurancesystem.saas.mapper;

import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

/**
 * 官网游客线索数据访问组件，负责把已经完成来源校验和业务校验的表单一次写入 SaaS 主库。
 * 合作诉求保持为 JSON 数组，不在数据访问层拆分或推断业务枚举。
 */
@Mapper
public interface VisitorLeadMapper {

    /**
     * 保存游客线索主记录。参数来自服务层标准化结果，返回的自增主键仅供内部排障，公开响应只暴露 leadNo。
     * 唯一编号冲突由服务层重新生成并重试，其他数据库约束异常按统一异常机制处理。
     */
    @Insert("INSERT INTO saas_visitor_lead(lead_no,name,contact,role_code,expected_monthly_orders,intent_codes,remark,created_at,updated_at,deleted) " +
            "VALUES(#{row.leadNo},#{row.name},#{row.contact},#{row.roleCode},#{row.expectedMonthlyOrders},CAST(#{row.intentCodesJson} AS JSON),#{row.remark},NOW(),NOW(),0)")
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insert(@Param("row") Map<String, Object> row);
}
