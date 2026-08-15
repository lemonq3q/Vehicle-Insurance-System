package com.example.insurancesystem.saas.mapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

@Mapper
public interface WorkorderOverageBillingMapper {
  @Select(
      "SELECT id FROM saas_subscription WHERE status=1 AND plan_id IS NOT NULL "
          + "AND start_at<=#{maintenanceEnd} AND end_at>#{maintenanceStart} ORDER BY enterprise_id,id")
  List<Long> findActiveSubscriptionIds(
      @Param("maintenanceStart") java.time.LocalDateTime maintenanceStart,
      @Param("maintenanceEnd") java.time.LocalDateTime maintenanceEnd);

  @Select("SELECT * FROM saas_subscription WHERE id=#{id} FOR UPDATE")
  Map<String, Object> lockSubscription(Long id);

  @Select(
      // last_overage_billing_date 为空表示从未支付过工单周期费，NOT EXISTS 条件不会排除该记录，因此允许本次计费。
      "SELECT id FROM (SELECT id,created_at,ROW_NUMBER() OVER (ORDER BY created_at,id) row_no "
          + "FROM biz_workorder WHERE enterprise_id=#{enterpriseId} AND deleted=0 "
          + "AND created_at < DATE_ADD(#{billingDate}, INTERVAL 1 DAY)) ranked "
          + "WHERE row_no>#{workorderLimit} AND DATEDIFF(#{billingDate},DATE(created_at))>=0 "
          + "AND MOD(DATEDIFF(#{billingDate},DATE(created_at)),#{cycleDays})=0 "
          + "AND NOT EXISTS (SELECT 1 FROM biz_workorder source WHERE source.id=ranked.id "
          + "AND source.last_overage_billing_date IS NOT NULL "
          + "AND DATEDIFF(#{billingDate},source.last_overage_billing_date)<#{cycleDays}) "
          + "ORDER BY created_at,id")
  List<Long> findDailyBillableWorkorderIds(
      @Param("enterpriseId") Long enterpriseId,
      @Param("workorderLimit") int workorderLimit,
      @Param("billingDate") LocalDate billingDate,
      @Param("cycleDays") int cycleDays);

  @Select(
      // 套餐变更沿用维护任务的周期规则：空扣费日可计费，非空时只有经过完整周期才可再次计费。
      "SELECT id FROM (SELECT id,created_at,ROW_NUMBER() OVER (ORDER BY created_at,id) row_no "
          + "FROM biz_workorder WHERE enterprise_id=#{enterpriseId} AND deleted=0) ranked "
          + "WHERE row_no>#{workorderLimit} AND NOT EXISTS (SELECT 1 FROM biz_workorder source "
          + "WHERE source.id=ranked.id AND source.last_overage_billing_date IS NOT NULL "
          + "AND DATEDIFF(#{billingDate},source.last_overage_billing_date)<#{cycleDays}) "
          + "ORDER BY created_at,id")
  List<Long> findCurrentExcessWorkorderIds(
      @Param("enterpriseId") Long enterpriseId,
      @Param("workorderLimit") int workorderLimit,
      @Param("billingDate") LocalDate billingDate,
      @Param("cycleDays") int cycleDays);

  @Update(
      // 最终写扣费日时再次校验 NULL 或完整周期，防止预览后并发请求对同一工单重复收费。
      "<script>UPDATE biz_workorder SET last_overage_billing_date=#{billingDate} "
          + "WHERE enterprise_id=#{enterpriseId} AND deleted=0 AND id IN "
          + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
          + "AND (last_overage_billing_date IS NULL "
          + "OR DATEDIFF(#{billingDate},last_overage_billing_date)&gt;=#{cycleDays})</script>")
  int markBilled(
      @Param("enterpriseId") Long enterpriseId,
      @Param("ids") List<Long> ids,
      @Param("billingDate") LocalDate billingDate,
      @Param("cycleDays") int cycleDays);
}
