package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.workorder.WorkorderFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
/**
 * 维护工单与企业文件之间的分类关联，并提供按工单聚合和批量替换能力。
 */
public interface WorkorderFileMapper extends BatchBaseMapper<WorkorderFile> {

    /**
     * 查询工单全部有效附件及其文件元数据。
     */
    List<WorkorderFile> selectByWorkorderId(Long id);

    /**
     * 按工单批量更新附件关系，用于以提交快照替换某阶段附件。
     */
    int batchUpdateByWorkorderId(@Param("list") List<WorkorderFile> list, @Param("workorderId") Long workorderId, @Param("updateBy") Long updateBy);
}
