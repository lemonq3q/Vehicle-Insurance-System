package com.example.insurancesystem.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.insurancesystem.config.mybatisplus.BatchBaseMapper;
import com.example.insurancesystem.domain.Insurance;
import com.example.insurancesystem.domain.SystemFile;
import com.example.insurancesystem.mapper.*;
import com.example.insurancesystem.utils.OSSUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Function;

@Component
public class DataArchive {

    @Autowired
    private InsuranceMapper insuranceMapper;

    @Autowired
    private MerchantMapper merchantMapper;

    @Autowired
    private MerchantAreaMapper merchantAreaMapper;

    @Autowired
    private SystemFileMapper systemFileMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private VehicleCertificateMapper vehicleCertificateMapper;

    @Autowired
    private VehicleInvoiceMapper vehicleInvoiceMapper;

    @Autowired
    private VehicleLicenseMapper vehicleLicenseMapper;

    @Autowired
    private WorkorderMapper workorderMapper;

    @Autowired
    private WorkorderFileMapper workorderFileMapper;

    @Autowired
    private WorkorderInsuranceMapper workorderInsuranceMapper;

    public void archive(){
        expiredFileClean();
        archiveDataBase();
    }

    public void archiveDataBase() {
        archiveTable(insuranceMapper);
        archiveTable(merchantMapper);
        archiveTable(merchantAreaMapper);
        archiveTable(systemFileMapper);
        archiveTable(userMapper);
        archiveTable(userRoleMapper);
        archiveTable(vehicleCertificateMapper);
        archiveTable(vehicleInvoiceMapper);
        archiveTable(vehicleLicenseMapper);
        archiveTable(workorderMapper);
        archiveTable(workorderFileMapper);
        archiveTable(workorderInsuranceMapper);
    }

    public void expiredFileClean(){
        // 计算：3天前的时间戳（毫秒）
        long threeDaysAgo = LocalDateTime.now()
                .minusDays(3)  // 减去3天
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        LambdaQueryWrapper<SystemFile> wrapper = new LambdaQueryWrapper<>();
        // 未删除
        wrapper.eq(SystemFile::getIsDelete, 0);
        // 未被引用
        wrapper.eq(SystemFile::getIsLinked, 0);
        // 最后更新时间 早于 3天前（超过3天没动过）
        wrapper.le(SystemFile::getUpdateTime, threeDaysAgo);

        List<SystemFile> fileList = systemFileMapper.selectList(wrapper);

        if (fileList == null || fileList.isEmpty()) {
            return;
        }

        for (SystemFile file : fileList) {
            try{
                OSSUtil.deleteFile(file.getPath());
                SystemFile update = new SystemFile();
                update.setId(file.getId());
                update.setIsDelete(1);
                systemFileMapper.updateById(update);
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    public <T> void archiveTable(BatchBaseMapper<T> mapper) {
        try {
            QueryWrapper<T> wrapper = new QueryWrapper<>();
            wrapper.eq("is_delete", 1);

            // 1. 查询已删除数据
            List<T> dataList = mapper.selectList(wrapper);
            if (dataList.isEmpty()) {
                return;
            }

            // 2. 批量插入归档
            ArchiveContext.setArchive();
            for (T data : dataList){
                mapper.insert(data);
            }
            ArchiveContext.clear();

            // 3. 删除原数据
            mapper.delete(wrapper);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ArchiveContext.clear();
        }
    }
}
