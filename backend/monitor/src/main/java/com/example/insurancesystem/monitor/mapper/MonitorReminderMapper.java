package com.example.insurancesystem.monitor.mapper;

import com.example.insurancesystem.monitor.domain.ReminderMergeRequest;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
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
    @Select("SELECT id,stage_level,process_status FROM monitor_enterprise_reminder WHERE enterprise_id=#{enterpriseId} AND reminder_key=#{reminderKey} FOR UPDATE")
    Map<String, Object> lock(@Param("enterpriseId") Long enterpriseId, @Param("reminderKey") String reminderKey);

    @Insert("INSERT INTO monitor_enterprise_reminder(enterprise_id,enterprise_name_snapshot,reminder_type,reminder_key,reminder_stage,stage_level,severity,title,content,business_data_json,revision,trigger_count,first_triggered_at,last_triggered_at,process_status,created_at,updated_at) VALUES(#{enterpriseId},#{enterpriseName},#{reminderType},#{reminderKey},#{reminderStage},#{stageLevel},#{severity},#{title},#{content},#{businessDataJson},1,1,#{triggeredAt},#{triggeredAt},0,NOW(),NOW())")
    int insert(ReminderMergeRequest request);

    @Update("UPDATE monitor_enterprise_reminder SET enterprise_name_snapshot=#{request.enterpriseName},reminder_stage=#{request.reminderStage},stage_level=#{request.stageLevel},severity=#{request.severity},title=#{request.title},content=#{request.content},business_data_json=#{request.businessDataJson},revision=revision+1,trigger_count=trigger_count+1,last_triggered_at=#{request.triggeredAt},last_processed_at=processed_at,last_processed_by=processed_by,last_process_remark=process_remark,process_status=0,processed_at=NULL,processed_by=NULL,process_remark=NULL,updated_at=NOW() WHERE id=#{id} AND stage_level<#{request.stageLevel}")
    int upgrade(@Param("id") Long id, @Param("request") ReminderMergeRequest request);
}
