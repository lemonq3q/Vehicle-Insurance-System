package com.example.insurancesystem.mapper;

import com.example.insurancesystem.domain.workorder.Workorder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 访问合并后的工单主表、金额表与物流关联。主表锁由服务事务持有，串行化阶段变更；
 * 部分更新保护历史资料，承保完整替换显式支持 NULL 清空，所有写入限制当前企业。
 */
@Mapper
public interface WorkorderAggregateMapper {
    /**
     * 锁定本企业有效工单，返回权限/状态/原完成时间，事务结束后释放锁。
     */
    Workorder lockWorkorder(@Param("id") Long id, @Param("enterpriseId") Long enterpriseId);
    /**
     * 查询金额行是否存在；在持有工单锁时用于补建，不能凭 UPDATE 影响行数判断存在。
     */
    int countQuote(Workorder workorder);
    /**
     * 仅更新已提交的报价或旧导入字段，不清空未提交的保费和政策。
     */
    int updateQuote(Workorder workorder);
    /**
     * 为金额资料尚未建立的工单插入空行，再由调用方保存实际字段。
     */
    int insertQuote(Workorder workorder);
    /**
     * 承保完整保存保费/税/政策，显式 NULL 表示取消险种或切换政策输入模式。
     */
    int replaceFinancials(Workorder workorder);
    /**
     * 承保完整保存三类保单，包括三种起期，取消险种时清空原字段。
     */
    int replacePolicies(Workorder workorder);
    /**
     * 将支付、核保与承保反馈写入主表，未提交字段保持不变。
     */
    int updateMainStage(Workorder workorder);
    /**
     * 更新本企业工单物流资料，不修改其他阶段信息。
     */
    int updateLogistics(Workorder workorder);
    /**
     * 为尚无物流资料的工单补建关系，由调用方事务负责一致性。
     */
    int insertLogistics(Workorder workorder);
}
