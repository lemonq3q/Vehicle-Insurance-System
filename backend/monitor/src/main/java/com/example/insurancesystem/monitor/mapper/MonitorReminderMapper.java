package com.example.insurancesystem.monitor.mapper;

import com.example.insurancesystem.monitor.domain.ReminderMergeRequest;
import java.util.Map;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 持久化客服提醒当前状态。查询锁与唯一键共同处理维护重试和并发请求；升级更新会把已处理提醒重新打开，
 * 同时将原处理信息移入最近处理快照，保证客服能识别该事项曾处理但风险已经升级。
 */
@Mapper
public interface MonitorReminderMapper {
    /** 判断跨服务传入的稳定类型编码是否已经注册且启用，避免产生无法分类的新提醒。 */
    @Select("SELECT COUNT(1) FROM sys_reminder_type WHERE type_code=#{typeCode} AND status=1")
    int countEnabledType(@Param("typeCode") String typeCode);

    @Select("SELECT id,stage_level,process_status FROM monitor_enterprise_reminder WHERE enterprise_id=#{enterpriseId} AND reminder_key=#{reminderKey} FOR UPDATE")
    Map<String, Object> lock(@Param("enterpriseId") Long enterpriseId, @Param("reminderKey") String reminderKey);

    @Insert("INSERT INTO monitor_enterprise_reminder(enterprise_id,enterprise_name_snapshot,reminder_type,reminder_key,reminder_stage,stage_level,severity,title,content,business_data_json,revision,trigger_count,first_triggered_at,last_triggered_at,process_status,created_at,updated_at) VALUES(#{enterpriseId},#{enterpriseName},#{reminderType},#{reminderKey},#{reminderStage},#{stageLevel},#{severity},#{title},#{content},#{businessDataJson},1,1,#{triggeredAt},#{triggeredAt},0,NOW(),NOW())")
    int insert(ReminderMergeRequest request);

    @Update("UPDATE monitor_enterprise_reminder SET enterprise_name_snapshot=#{request.enterpriseName},reminder_stage=#{request.reminderStage},stage_level=#{request.stageLevel},severity=#{request.severity},title=#{request.title},content=#{request.content},business_data_json=#{request.businessDataJson},revision=revision+1,trigger_count=trigger_count+1,last_triggered_at=#{request.triggeredAt},last_processed_at=processed_at,last_processed_by=processed_by,last_process_remark=process_remark,process_status=0,processed_at=NULL,processed_by=NULL,process_remark=NULL,updated_at=NOW() WHERE id=#{id} AND stage_level<#{request.stageLevel}")
    int upgrade(@Param("id") Long id, @Param("request") ReminderMergeRequest request);

    /** 只允许每日维护清理已恢复的自动续费套餐下架提醒，其他提醒不提供自动删除能力。 */
    @Delete("DELETE FROM monitor_enterprise_reminder WHERE enterprise_id=#{enterpriseId} AND reminder_key=#{reminderKey} AND reminder_type='AUTO_RENEW_PLAN_UNAVAILABLE'")
    int deleteAutoRenewPlanUnavailable(@Param("enterpriseId") Long enterpriseId,
            @Param("reminderKey") String reminderKey);

    /**
     * 根据监控页面的可选条件分页读取提醒。类型名称由字典关联获得；LEFT JOIN 保证历史未知编码仍可展示，
     * 企业名称优先使用触发快照，避免企业更名使提醒正文和列表名称产生冲突。
     */
    @Select("<script>SELECT r.id,r.enterprise_id,r.enterprise_name_snapshot,e.code enterprise_code," +
            "e.contact_name enterprise_contact_name,e.contact_phone enterprise_contact_phone,r.reminder_type," +
            "COALESCE(t.type_name,r.reminder_type) type_name,c.category_code,c.category_name," +
            "r.reminder_stage,r.stage_level,r.severity,r.title,r.content,r.revision,r.trigger_count," +
            "r.first_triggered_at,r.last_triggered_at,r.process_status,r.processed_at,r.processed_by,r.process_remark " +
            "FROM monitor_enterprise_reminder r LEFT JOIN tenant_enterprise e ON e.id=r.enterprise_id AND e.deleted=0 " +
            "LEFT JOIN sys_reminder_type t ON t.type_code=r.reminder_type " +
            "LEFT JOIN sys_reminder_category c ON c.id=t.category_id WHERE 1=1 " +
            "<if test='severity != null and severity != &quot;&quot;'>AND r.severity=#{severity} </if>" +
            "<if test='categoryCode != null and categoryCode != &quot;&quot;'>AND c.category_code=#{categoryCode} </if>" +
            "<if test='typeCodes != null and typeCodes != &quot;&quot;'>AND FIND_IN_SET(r.reminder_type,#{typeCodes}) &gt; 0 </if>" +
            "<if test='enterpriseId != null'>AND r.enterprise_id=#{enterpriseId} </if>" +
            "<if test='processStatus != null'>AND r.process_status=#{processStatus} </if>" +
            "ORDER BY CASE r.severity WHEN 'CRITICAL' THEN 3 WHEN 'WARNING' THEN 2 ELSE 1 END DESC,r.last_triggered_at DESC,r.id DESC " +
            "LIMIT #{offset},#{pageSize}</script>")
    List<Map<String, Object>> findPage(@Param("severity") String severity, @Param("categoryCode") String categoryCode,
            @Param("typeCodes") String typeCodes, @Param("enterpriseId") Long enterpriseId,
            @Param("processStatus") Integer processStatus, @Param("offset") int offset, @Param("pageSize") int pageSize);

    /** 使用与列表完全相同的条件统计总数，使分页元数据不会因字典关联或筛选条件产生偏差。 */
    @Select("<script>SELECT COUNT(1) FROM monitor_enterprise_reminder r " +
            "LEFT JOIN sys_reminder_type t ON t.type_code=r.reminder_type LEFT JOIN sys_reminder_category c ON c.id=t.category_id WHERE 1=1 " +
            "<if test='severity != null and severity != &quot;&quot;'>AND r.severity=#{severity} </if>" +
            "<if test='categoryCode != null and categoryCode != &quot;&quot;'>AND c.category_code=#{categoryCode} </if>" +
            "<if test='typeCodes != null and typeCodes != &quot;&quot;'>AND FIND_IN_SET(r.reminder_type,#{typeCodes}) &gt; 0 </if>" +
            "<if test='enterpriseId != null'>AND r.enterprise_id=#{enterpriseId} </if>" +
            "<if test='processStatus != null'>AND r.process_status=#{processStatus} </if></script>")
    long countPage(@Param("severity") String severity, @Param("categoryCode") String categoryCode,
            @Param("typeCodes") String typeCodes, @Param("enterpriseId") Long enterpriseId,
            @Param("processStatus") Integer processStatus);

    /** 一次查询返回启用的类别和具体类型；企业选项改由关键词接口按需获取，避免首屏拉取全部企业。 */
    @Select("SELECT 'TYPE' AS optionKind,c.category_code AS categoryCode,c.category_name AS categoryName," +
            "t.type_code AS typeCode,t.type_name AS typeName " +
            "FROM sys_reminder_type t JOIN sys_reminder_category c ON c.id=t.category_id " +
            "WHERE c.status=1 AND t.status=1 ORDER BY c.sort_no,t.sort_no,t.id")
    List<Map<String, Object>> findFilterOptions();

    /** 按企业名称或编码模糊搜索正常业务数据，固定上限防止联想输入造成大结果集。 */
    @Select("SELECT id,name,code FROM tenant_enterprise WHERE deleted=0 " +
            "AND (name LIKE CONCAT('%',#{keyword},'%') OR code LIKE CONCAT('%',#{keyword},'%')) " +
            "ORDER BY CASE WHEN name LIKE CONCAT(#{keyword},'%') THEN 0 WHEN code LIKE CONCAT(#{keyword},'%') THEN 1 ELSE 2 END,name,id LIMIT 20")
    List<Map<String, Object>> searchEnterprises(@Param("keyword") String keyword);

    @Select("SELECT id,enterprise_id,enterprise_name_snapshot,reminder_type,title,process_status,revision,process_remark " +
            "FROM monitor_enterprise_reminder WHERE id=#{id} FOR UPDATE")
    Map<String, Object> lockForProcessing(@Param("id") Long id);

    /** 使用版本号完成处理，避免操作员确认弹窗停留期间被新的更高风险阶段覆盖后仍误标已处理。 */
    @Update("UPDATE monitor_enterprise_reminder SET process_status=1,processed_at=NOW(),processed_by=#{userId},process_remark=#{remark},revision=revision+1,updated_at=NOW() WHERE id=#{id} AND revision=#{revision} AND process_status=0")
    int markProcessed(@Param("id") Long id, @Param("revision") int revision, @Param("userId") Long userId, @Param("remark") String remark);

    /**
     * 将人工误处理的提醒恢复到待处理队列。恢复前把本次处理信息保存到最近处理快照，
     * 再清空当前处理人、时间和备注；版本条件防止覆盖并发升级或其他操作员的更新。
     */
    @Update("UPDATE monitor_enterprise_reminder SET last_processed_at=processed_at,last_processed_by=processed_by," +
            "last_process_remark=process_remark,process_status=0,processed_at=NULL,processed_by=NULL,process_remark=NULL," +
            "revision=revision+1,updated_at=NOW() WHERE id=#{id} AND revision=#{revision} AND process_status=1")
    int restoreUnprocessed(@Param("id") Long id, @Param("revision") int revision);
}
