package com.example.insurancesystem.saas.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 查询提醒计算所需的订阅、余额和工单用量快照，并维护企业门户提醒当前状态。
 * 用量严格使用订阅快照额度与订阅周期，避免套餐主数据调整后反向改变历史周期的提醒判断。
 */
@Mapper
public interface ReminderMapper {
    @Select("SELECT s.id subscription_id,s.enterprise_id,s.plan_id,s.status subscription_status,s.suspend_reason,s.suspended_at,s.start_at,s.end_at,s.next_renew_at,s.auto_renew_enabled,s.workorder_limit,e.name enterprise_name,e.code enterprise_code,p.name plan_name,COALESCE(rp.price,p.price,0) renewal_amount,w.id wallet_id,COALESCE(w.balance_amount,0) balance_amount,COALESCE(w.frozen_amount,0) frozen_amount,(SELECT MAX(t.id) FROM saas_wallet_transaction t WHERE t.wallet_id=w.id AND t.balance_before>=0 AND t.balance_after<0) arrears_cycle_id,(SELECT COUNT(1) FROM biz_workorder b WHERE b.enterprise_id=s.enterprise_id AND b.deleted=0 AND b.created_at>=s.start_at AND b.created_at<s.end_at) used_count FROM saas_subscription s JOIN tenant_enterprise e ON e.id=s.enterprise_id AND e.deleted=0 LEFT JOIN saas_plan p ON p.id=s.plan_id AND p.deleted=0 LEFT JOIN saas_plan rp ON rp.id=COALESCE(s.auto_renew_plan_id,s.plan_id) AND rp.deleted=0 LEFT JOIN saas_wallet w ON w.enterprise_id=s.enterprise_id AND w.deleted=0 WHERE s.plan_id IS NOT NULL AND s.end_at IS NOT NULL ORDER BY s.enterprise_id")
    List<Map<String, Object>> findReminderCandidates();

    @Insert("INSERT IGNORE INTO saas_enterprise_reminder(enterprise_id,reminder_type,reminder_key,reminder_stage,stage_level,severity,title,content,business_data_json,revision,trigger_count,first_triggered_at,last_triggered_at,expires_at,is_read,created_at,updated_at) VALUES(#{enterpriseId},#{reminderType},#{reminderKey},#{reminderStage},#{stageLevel},#{severity},#{title},#{content},#{businessDataJson},1,1,#{triggeredAt},#{triggeredAt},#{expiresAt},0,NOW(),NOW())")
    int insert(Map<String, Object> reminder);

    @Update("UPDATE saas_enterprise_reminder SET reminder_stage=#{reminderStage},stage_level=#{stageLevel},severity=#{severity},title=#{title},content=#{content},business_data_json=#{businessDataJson},revision=revision+1,trigger_count=trigger_count+1,last_triggered_at=#{triggeredAt},expires_at=#{expiresAt},is_read=0,read_at=NULL,read_by=NULL,updated_at=NOW() WHERE enterprise_id=#{enterpriseId} AND reminder_key=#{reminderKey} AND stage_level<#{stageLevel}")
    int upgrade(Map<String, Object> reminder);

    /** 最近一个月指数据库当前时刻向前推一个自然月，并排除已经达到业务失效时间的提醒。 */
    @Select("SELECT id,reminder_type,reminder_stage,severity,title,content,last_triggered_at occurred_at,revision FROM saas_enterprise_reminder WHERE enterprise_id=#{enterpriseId} AND last_triggered_at>=DATE_SUB(NOW(),INTERVAL 1 MONTH) AND (expires_at IS NULL OR expires_at>NOW()) ORDER BY CASE severity WHEN 'CRITICAL' THEN 3 WHEN 'WARNING' THEN 2 ELSE 1 END DESC,last_triggered_at DESC,id DESC")
    List<Map<String, Object>> findRecent(@Param("enterpriseId") Long enterpriseId);
}
