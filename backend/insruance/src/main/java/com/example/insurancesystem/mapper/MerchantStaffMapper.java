package com.example.insurancesystem.mapper;

import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.merchant.MerchantStaff;
import com.example.insurancesystem.domain.user.MerchantUserDTO;
import com.example.insurancesystem.domain.user.MerchantUserSearchDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
/**
 * 访问商户员工主体，并聚合员工当前角色用于联系人和收款人管理。
 */
public interface MerchantStaffMapper extends BatchBaseMapper<MerchantStaff> {
    /**
     * 批量写入商户人员并回填数据库自增主键。人员角色必须在本方法完成后组装，才能通过 staff_id
     * 与数据库中的真实人员记录建立一致关联。
     */
    int insertImportBatch(@Param("list") List<MerchantStaff> staffList);

    /**
     * 按商户、角色和关键字查询员工聚合列表。
     */
    List<MerchantUserDTO> selectStaff(MerchantUserSearchDTO params);
    /**
     * 查询单个员工及其有效角色详情。
     */
    MerchantUserDTO selectStaffById(Long id);
    /**
     * 查询指定商户下可供业务表单选择的有效员工。
     */
    List<MerchantUserDTO> selectOptionsByMerchantId(Long merchantId);
    /**
     * 统计商户内指定角色人数，更新时可排除当前员工以校验唯一角色。
     */
    Long countRole(Long merchantId, String roleCode, Long excludeStaffId);

    /**
     * 批量查询当前企业已使用的手机号，避免导入数据与已有商户人员发生冲突。
     */
    List<String> selectExistingPhones(@Param("enterpriseId") Long enterpriseId,
                                      @Param("phones") List<String> phones);
}
