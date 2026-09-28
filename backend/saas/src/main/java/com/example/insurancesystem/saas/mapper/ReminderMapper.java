package com.example.insurancesystem.saas.mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 查询提醒计算所需的订阅、余额和工单用量快照，并维护企业门户提醒当前状态。
 * 用量严格使用订阅快照额度与订阅周期，避免套餐主数据调整后反向改变历史周期的提醒判断。
 */
@Mapper
public interface ReminderMapper {
    String CANDIDATE_SQL = "SELECT s.id subscription_id,s.enterprise_id,s.plan_id,s.status subscription_status,s.suspend_reason,s.suspended_at,s.start_at,s.end_at,s.next_renew_at,s.auto_renew_enabled,s.auto_renew_plan_id,s.workorder_limit,e.name enterprise_name,e.code enterprise_code,p.name plan_name,rp.name renewal_plan_name,rp.status renewal_plan_status,COALESCE(rp.price,p.price,0) renewal_amount,w.id wallet_id,COALESCE(w.balance_amount,0) balance_amount,COALESCE(w.frozen_amount,0) frozen_amount,(SELECT MAX(t.id) FROM saas_wallet_transaction t WHERE t.wallet_id=w.id AND t.balance_before>=0 AND t.balance_after&lt;0) arrears_cycle_id,(SELECT COUNT(1) FROM biz_workorder b WHERE b.enterprise_id=s.enterprise_id AND b.deleted=0 AND b.created_at>=s.start_at AND b.created_at&lt;s.end_at) used_count FROM saas_subscription s JOIN tenant_enterprise e ON e.id=s.enterprise_id AND e.deleted=0 LEFT JOIN saas_plan p ON p.id=s.plan_id AND p.deleted=0 LEFT JOIN saas_plan rp ON rp.id=COALESCE(s.auto_renew_plan_id,s.plan_id) AND rp.deleted=0 LEFT JOIN saas_wallet w ON w.enterprise_id=s.enterprise_id AND w.deleted=0 WHERE s.plan_id IS NOT NULL AND s.end_at IS NOT NULL";

    @Select("<script>" + CANDIDATE_SQL + " ORDER BY s.enterprise_id</script>")
    List<Map<String, Object>> findReminderCandidates();

    /**
     * 定时复核只读取本批生效提醒所属企业，复用每日生产的快照字段及工单周期语义。
     * 调用方保证非空且最多500个企业，避免逐条提醒查询和无界企业扫描。
     */
    @Select("<script>" + CANDIDATE_SQL + " AND s.enterprise_id IN "
            + "<foreach collection='enterpriseIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Map<String, Object>> findReminderCandidatesByEnterpriseIds(@Param("enterpriseIds") List<Long> enterpriseIds);

    @Insert("INSERT IGNORE INTO saas_enterprise_reminder(enterprise_id,reminder_type,reminder_key,reminder_stage,stage_level,severity,title,content,business_data_json,revision,trigger_count,first_triggered_at,last_triggered_at,expires_at,is_read,created_at,updated_at) VALUES(#{enterpriseId},#{reminderType},#{reminderKey},#{reminderStage},#{stageLevel},#{severity},#{title},#{content},#{businessDataJson},1,1,#{triggeredAt},#{triggeredAt},#{expiresAt},0,NOW(),NOW())")
    int insert(Map<String, Object> reminder);

    @Update("UPDATE saas_enterprise_reminder SET reminder_stage=#{reminderStage},stage_level=#{stageLevel},severity=#{severity},title=#{title},content=#{content},business_data_json=#{businessDataJson},is_active=1,invalidated_at=NULL,lifecycle_version=lifecycle_version+1,sync_pending=1,revision=revision+1,trigger_count=trigger_count+1,last_triggered_at=#{triggeredAt},expires_at=#{expiresAt},is_read=0,read_at=NULL,read_by=NULL,updated_at=NOW() WHERE enterprise_id=#{enterpriseId} AND reminder_key=#{reminderKey} AND (stage_level<#{stageLevel} OR is_active=0)")
    int upgrade(Map<String, Object> reminder);

    /**
     * 同一周期风险减轻时更新阶段与业务文案，并递增生命周期版本供监控接受降级快照。
     * 仅处理仍生效且阶段严格下降的记录；保留已读、触发次数和最近触发时间，避免降级重复打扰用户。
     */
    @Update("UPDATE saas_enterprise_reminder SET reminder_stage=#{reminderStage},stage_level=#{stageLevel},severity=#{severity},title=#{title},content=#{content},business_data_json=#{businessDataJson},expires_at=#{expiresAt},lifecycle_version=lifecycle_version+1,sync_pending=1,revision=revision+1,updated_at=NOW() WHERE enterprise_id=#{enterpriseId} AND reminder_key=#{reminderKey} AND is_active=1 AND stage_level>#{stageLevel}")
    int downgrade(Map<String, Object> reminder);

    /**
     * 当自动续费目标套餐重新可用时，按当前订阅周期的稳定键删除企业门户提醒。
     * enterpriseId 与 reminderKey 双条件避免跨企业或跨续费周期误删。
     */
    @Delete("DELETE FROM saas_enterprise_reminder WHERE enterprise_id=#{enterpriseId} AND reminder_key=#{reminderKey} AND reminder_type='AUTO_RENEW_PLAN_UNAVAILABLE'")
    int deleteAutoRenewPlanUnavailable(@Param("enterpriseId") Long enterpriseId,
            @Param("reminderKey") String reminderKey);

    /** 生效提醒不按年龄或旧expires_at过滤；业务条件复核负责标记失效，历史数据不删除。 */
    @Select("SELECT id,reminder_type,reminder_stage,severity,title,content,last_triggered_at occurred_at,revision FROM saas_enterprise_reminder WHERE enterprise_id=#{enterpriseId} AND is_active=1 ORDER BY CASE severity WHEN 'CRITICAL' THEN 3 WHEN 'WARNING' THEN 2 ELSE 1 END DESC,last_triggered_at DESC,id DESC")
    List<Map<String, Object>> findRecent(@Param("enterpriseId") Long enterpriseId);
    /**
     * 生效扫描及待同步补偿分别使用复合索引和主键游标；单批大小由配置限制。
     * 游标固定向前移动，避免前批网络故障阻塞后续提醒，下一轮再从头重试失败数据。
     */
    @Select("SELECT * FROM saas_enterprise_reminder WHERE is_active=1 AND id>#{afterId} ORDER BY id LIMIT #{batchSize}")
    List<Map<String, Object>> findActiveBatch(@Param("afterId") long afterId, @Param("batchSize") int batchSize);

    /**
     * 读取待同步记录的最新持久化版本，而非重用请求中可能已经过时的快照。
     */
    @Select("SELECT * FROM saas_enterprise_reminder WHERE sync_pending=1 AND id>#{afterId} ORDER BY id LIMIT #{batchSize}")
    List<Map<String, Object>> findPendingSyncBatch(@Param("afterId") long afterId, @Param("batchSize") int batchSize);

    /**
     * 条件失效只修改生效状态和版本，保留标题、业务快照及已读历史；版本检查不覆盖并发风险升级。
     */
    @Update("UPDATE saas_enterprise_reminder SET is_active=0,invalidated_at=#{now},lifecycle_version=lifecycle_version+1,"
            + "sync_pending=1,revision=revision+1,updated_at=NOW() WHERE id=#{id} AND is_active=1 AND lifecycle_version=#{version}")
    int invalidate(@Param("id") long id, @Param("version") long version, @Param("now") LocalDateTime now);

    /**
     * 取得稳定业务键的当前状态用于即时同步；同键升级或重新生效后均发送数据库中的权威版本。
     */
    @Select("SELECT * FROM saas_enterprise_reminder WHERE enterprise_id=#{enterpriseId} AND reminder_key=#{reminderKey}")
    Map<String, Object> findByKey(@Param("enterpriseId") long enterpriseId, @Param("reminderKey") String reminderKey);

    /**
     * 仅确认已经发送的版本；若同步期间发生升级，旧响应不能清除新版本的待同步标记。
     */
    @Update("UPDATE saas_enterprise_reminder SET sync_pending=0 WHERE id=#{id} AND lifecycle_version=#{version}")
    int acknowledgeSync(@Param("id") long id, @Param("version") long version);
}
