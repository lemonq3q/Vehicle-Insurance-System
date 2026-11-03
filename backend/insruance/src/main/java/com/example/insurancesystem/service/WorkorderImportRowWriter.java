package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.workorder.WorkorderDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 为每条通过校验的历史工单建立独立事务，单行失败不留下孤立报价、证件或险种记录。
 * 与导入解析服务分成两个 Spring Bean，保证事务代理实际生效；同一请求逐行处理，未开启异步线程。
 */
@Service
public class WorkorderImportRowWriter {
    private final WorkorderService service;

    /**
     * 复用现有工单聚合服务，不绕过编号生成和关联表保存流程。
     * @param service 当前工单服务代理。
     */
    public WorkorderImportRowWriter(WorkorderService service) { this.service = service; }

    /**
     * 在独立事务内保存一行完整工单，运行时失败回滚本行所有写入。
     * @param workorder 已匹配机构、处理人并生成险种快照的导入模型。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void write(WorkorderDTO workorder) { service.insertImported(workorder); }
}
