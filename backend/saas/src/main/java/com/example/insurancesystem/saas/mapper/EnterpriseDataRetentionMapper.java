package com.example.insurancesystem.saas.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 查询已经超过车险业务资料保留期的企业。
 * saas_subscription 在当前模型中每个企业仅保留一条当前订阅状态，其 end_at 即最近一次开通套餐的结束时间；
 * 查询不修改订阅、成员或钱包，只把符合阈值的企业主键交给跨服务清理流程。
 */
@Mapper
public interface EnterpriseDataRetentionMapper {
    /**
     * 查询最近套餐结束时间不晚于清理截止时间的企业。plan_id/end_at 非空确保从未订阅套餐的企业不会被处理，
     * 按结束时间和企业主键排序使批处理日志及故障重试顺序稳定。
     *
     * @param cutoff 当前维护时间减去保留天数得到的截止时间
     * @return 待清理企业主键列表
     */
    @Select("SELECT enterprise_id FROM saas_subscription "
            + "WHERE plan_id IS NOT NULL AND end_at IS NOT NULL AND end_at<=#{cutoff} "
            + "ORDER BY end_at,enterprise_id")
    List<Long> findEnterpriseIdsPastRetention(@Param("cutoff") LocalDateTime cutoff);
}
