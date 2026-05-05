package com.example.insurancesystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.insurancesystem.domain.Insurance;
import com.example.insurancesystem.domain.SystemFile;
import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.example.insurancesystem.domain.encapsulate.TableData;
import com.example.insurancesystem.domain.merchant.Merchant;
import com.example.insurancesystem.domain.user.User;
import com.example.insurancesystem.domain.workorder.*;
import com.example.insurancesystem.mapper.*;
import com.example.insurancesystem.service.InsuranceService;
import com.example.insurancesystem.service.WorkorderService;
import com.example.insurancesystem.utils.OSSUtil;
import com.example.insurancesystem.utils.SystemCommonUtil;
import com.github.pagehelper.PageHelper;
import org.apache.commons.collections4.map.HashedMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class WorkorderServiceImpl implements WorkorderService {

    @Autowired
    private WorkorderMapper workorderMapper;

    @Autowired
    private WorkorderInsuranceMapper workorderInsuranceMapper;

    @Autowired
    private VehicleLicenseMapper vehicleLicenseMapper;

    @Autowired
    private VehicleCertificateMapper vehicleCertificateMapper;

    @Autowired
    private VehicleInvoiceMapper vehicleInvoiceMapper;

    @Autowired
    private SystemFileMapper systemFileMapper;

    @Autowired
    private WorkorderFileMapper workorderFileMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MerchantMapper merchantMapper;

    @Autowired
    private OSSUtil ossUtil;

    @Autowired
    private InsuranceService insuranceService;

    private static final Long  EXPIRATION_TIME = 365L;

    private static final Long REMIND_TIME = 3000L;

    @Override
    public ResponseResult select(WorkorderSearchDTO params) {
        PageHelper.startPage(params.getPageNum(), params.getPageSize());

        if(!SystemCommonUtil.hasPerm("all")){
            params.setHandleUserId(SystemCommonUtil.getNowUserId());
        }

        List<WorkorderDTO> workorderDTOList = workorderMapper.selectByWorkorderSearchDTO(params);
        TableData<WorkorderDTO> tableData = new TableData<>(workorderDTOList);
        tableData.getTable().forEach(WorkorderDTO::compute);
        return new ResponseResult(200, tableData);
    }

    @Override
    public ResponseResult selectRenew(WorkorderSearchDTO params) {
        PageHelper.startPage(params.getPageNum(), params.getPageSize());

        if(!SystemCommonUtil.hasPerm("all")){
            params.setCreateBy(SystemCommonUtil.getNowUserId());
        }

        long nowSec = System.currentTimeMillis() / 1000;
        long beginTime = nowSec - EXPIRATION_TIME * 24 * 60 * 60;
        long endTime = nowSec - (EXPIRATION_TIME - REMIND_TIME) * 24 * 60 * 60;
        params.setBeginTime(beginTime);
        params.setEndTime(endTime);
        params.setStatus(10);

        List<WorkorderDTO> workorderDTOList = workorderMapper.selectRenewByWorkorderSearchDTO(params);
        TableData<WorkorderDTO> tableData = new TableData<>(workorderDTOList);
        tableData.getTable().forEach(WorkorderDTO::compute);
        return new ResponseResult(200, tableData);
    }

    @Override
    public ResponseResult selectRenewCount() {

        long nowSec = System.currentTimeMillis() / 1000;
        long beginTime = nowSec - EXPIRATION_TIME * 24 * 60 * 60;
        long endTime = nowSec - (EXPIRATION_TIME - REMIND_TIME) * 24 * 60 * 60;

        // 如果访问的管理员，同时获取管理员自己的快过期的保单数量和所有人的数量
        // 如果是录单员，只获取自己的快过期保单数量
        Map<String, Integer> countMap = new HashedMap<>();

        Long nowUserId = SystemCommonUtil.getNowUserId();
        if(SystemCommonUtil.hasPerm("all")){
            LambdaQueryWrapper<Workorder> selfWrapper = new LambdaQueryWrapper<>();
            selfWrapper.eq(Workorder::getIsDelete, 0);
            selfWrapper.eq(Workorder::getStatus, 10);
            selfWrapper.and(w -> w.eq(Workorder::getRemindStatus, 0).or().isNull(Workorder::getRemindStatus));
            selfWrapper.eq(Workorder::getCreateBy, nowUserId);
            selfWrapper.and(w -> w.between(Workorder::getCommercialInsuranceStartTime, beginTime, endTime)
                    .or()
                    .between(Workorder::getCompulsoryInsuranceStartTime, beginTime, endTime));
            Integer selfCount = workorderMapper.selectCount(selfWrapper).intValue();

            LambdaQueryWrapper<Workorder> allWrapper = new LambdaQueryWrapper<>();
            allWrapper.eq(Workorder::getIsDelete, 0);
            allWrapper.eq(Workorder::getStatus, 10);
            allWrapper.and(w -> w.eq(Workorder::getRemindStatus, 0).or().isNull(Workorder::getRemindStatus));
            allWrapper.and(w -> w.between(Workorder::getCommercialInsuranceStartTime, beginTime, endTime)
                    .or()
                    .between(Workorder::getCompulsoryInsuranceStartTime, beginTime, endTime));
            Integer allCount = workorderMapper.selectCount(allWrapper).intValue();

            countMap.put("selfCount", selfCount);
            countMap.put("allCount", allCount);
            return new ResponseResult(200, countMap);
        }
        else{
            LambdaQueryWrapper<Workorder> selfWrapper = new LambdaQueryWrapper<>();
            selfWrapper.eq(Workorder::getIsDelete, 0);
            selfWrapper.eq(Workorder::getStatus, 10);
            selfWrapper.and(w -> w.eq(Workorder::getRemindStatus, 0).or().isNull(Workorder::getRemindStatus));
            selfWrapper.eq(Workorder::getCreateBy, nowUserId);
            selfWrapper.and(w -> w.between(Workorder::getCommercialInsuranceStartTime, beginTime, endTime)
                    .or()
                    .between(Workorder::getCompulsoryInsuranceStartTime, beginTime, endTime));
            Integer selfCount = workorderMapper.selectCount(selfWrapper).intValue();

            countMap.put("selfCount", selfCount);
            return new ResponseResult(200, countMap);
        }
    }

    @Override
    public List<WorkorderExcelDTO> getExcel(WorkorderSearchDTO params) {
        if(!SystemCommonUtil.hasPerm("all")){
            params.setHandleUserId(SystemCommonUtil.getNowUserId());
        }
        List<WorkorderDTO> workorderDTOList = workorderMapper.selectByWorkorderSearchDTO(params);
        return workorderDTOList.stream()
                .peek(dto -> dto.compute(false))
                .map(WorkorderExcelDTO::new)
                .toList();
    }

    @Override
    public ResponseResult selectById(Long id) {
        WorkorderSearchDTO params = new WorkorderSearchDTO();
        params.setId(id);
        List<WorkorderDTO> workorderDTOList = workorderMapper.selectByWorkorderSearchDTO(params);
        if (workorderDTOList.isEmpty()){
            return new ResponseResult(404, "无资源");
        }
        WorkorderDTO workorderDTO = workorderDTOList.get(0);

        if(!SystemCommonUtil.hasPerm("all") && !workorderDTO.getHandleBy().equals(SystemCommonUtil.getNowUserId())){
            return new ResponseResult(403, "你无权访问此资源");
        }

        LambdaQueryWrapper<WorkorderInsurance> insuranceWrapper = new LambdaQueryWrapper<>();
        insuranceWrapper.eq(WorkorderInsurance::getWorkorderId, workorderDTO.getId());
        insuranceWrapper.eq(WorkorderInsurance::getIsDelete, 0);
        List<WorkorderInsurance> workorderInsuranceList = workorderInsuranceMapper.selectList(insuranceWrapper);
        workorderDTO.setWorkorderInsuranceList(workorderInsuranceList);

        List<WorkorderFile> workorderFileList = workorderFileMapper.selectByWorkorderId(workorderDTO.getId());

        // 获取临时文件url
        workorderFileList.forEach(item -> {
            item.setPath(ossUtil.getTmpUrl(item.getPath()));
        });

        workorderDTO.setWorkorderFileList(workorderFileList);

        return new ResponseResult(200, workorderDTO);
    }

    @Override
    public ResponseResult insert(WorkorderDTO params) {
        Long nowUserId = SystemCommonUtil.getNowUserId();
        Workorder workorder = new Workorder(params);
        workorder.setCode(SystemCommonUtil.buildCode());

        // 自动添加商户信息
        Long createMerchantId = workorder.getCreateMerchantId();
        Merchant createMerchant = merchantMapper.selectById(createMerchantId);
        workorder.setPayBank(createMerchant.getBank());
        workorder.setPayBankCardNum(createMerchant.getBankCardNum());
//        List<User> payees = userMapper.selectPayeeByMerchantId(createMerchantId);
        List<User> payees = userMapper.selectUserByMerchantId(createMerchantId);
        if(!payees.isEmpty()){
            User payee = null;
            int flag = 0;
            for(User item : payees){
                if(item.getRoleName().equals("收款人")){
                    payee = item;
                    flag = 3;
                }
                else if(flag <= 1 && item.getRoleName().equals("联系人")){
                    payee = item;
                    flag = 2;
                }
                else if(flag == 0){
                    payee = item;
                    flag = 1;
                }
            }
            workorder.setPayName(payee.getName());
            workorder.setPayIdNum(payee.getUsername());
        }
        workorder.setUpdateBy(nowUserId);
        workorder.setHandleBy(nowUserId);
        workorder.setStatus(2);  // 自动接单，进入待报价阶段
        int x = workorderMapper.insert(workorder);

        List<WorkorderInsurance> workorderInsuranceList = params.getWorkorderInsuranceList();
        workorderInsuranceList.forEach(item -> {
            item.setWorkorderId(workorder.getId());
            item.setUpdateBy(SystemCommonUtil.getNowUserId());
            item.setIsDelete(0);
        });
        workorderInsuranceMapper.insertBatchSomeColumn(workorderInsuranceList);

        if (params.getType() == 0){
            VehicleLicense vehicleLicense = params.getVehicleLicense();
            vehicleLicense.setWorkorderId(workorder.getId());
            vehicleLicense.setUpdateBy(SystemCommonUtil.getNowUserId());
            vehicleLicenseMapper.insert(vehicleLicense);
        }
        else {
            VehicleCertificate vehicleCertificate = params.getVehicleCertificate();
            vehicleCertificate.setWorkorderId(workorder.getId());
            vehicleCertificate.setUpdateBy(SystemCommonUtil.getNowUserId());
            vehicleCertificateMapper.insert(vehicleCertificate);

            VehicleInvoice vehicleInvoice = params.getVehicleInvoice();
            vehicleInvoice.setWorkorderId(workorder.getId());
            vehicleInvoice.setUpdateBy(SystemCommonUtil.getNowUserId());
            vehicleInvoiceMapper.insert(vehicleInvoice);
        }

        List<WorkorderFile> workorderFileList = params.getWorkorderFileList();
        if(workorderFileList != null && !workorderFileList.isEmpty()){
            workorderFileList.forEach(item -> {
                item.setWorkorderId(workorder.getId());
                item.setUpdateBy(SystemCommonUtil.getNowUserId());
                item.setIsDelete(0);
            });
            workorderFileMapper.insertBatchSomeColumn(workorderFileList);

            List<Long> fileIds = workorderFileList.stream()
                    .map(WorkorderFile::getFileId)
                    .toList();
            LambdaUpdateWrapper<SystemFile> fileWrapper = new LambdaUpdateWrapper<>();
            fileWrapper.in(SystemFile::getId, fileIds);
            fileWrapper.set(SystemFile::getUpdateBy, SystemCommonUtil.getNowUserId());
            fileWrapper.set(SystemFile::getIsLinked, 1);
            systemFileMapper.update(null, fileWrapper);
        }

        return new ResponseResult(200, "已新增" + x + "条数据", workorder);
    }

    @Override
    public ResponseResult updateBaseInfo(WorkorderDTO params) {
        Long updateBy = SystemCommonUtil.getNowUserId();
        Workorder workorder = new Workorder(params);
        workorder.setUpdateBy(updateBy);
        List<WorkorderInsurance> workorderInsuranceList = params.getWorkorderInsuranceList();
        workorderInsuranceMapper.batchUpdateByWorkorderId(workorderInsuranceList, workorder.getId(), updateBy);

        List<Insurance> insuranceList = insuranceService.selectAll().getData();
        int type1Count = 0;
        int type3Count = 0;
        for(WorkorderInsurance item : workorderInsuranceList){
            if(!item.getOptionJson().equals("不投保") && !item.getOptionJson().equals("不需要")){
                Insurance insurance = insuranceList.stream()
                        .filter(insurance1 -> insurance1.getId().equals(item.getInsuranceId()))
                        .findFirst()
                        .orElse(null);
                if(insurance != null && insurance.getType() == 1){
                    type1Count++;
                }
                else if(insurance != null && insurance.getType() == 3){
                    type3Count++;
                }
            }
        }

        LambdaUpdateWrapper<Workorder> workorderWrapper = new LambdaUpdateWrapper<>();
        workorderWrapper.eq(Workorder::getId, workorder.getId());
        workorderWrapper.eq(Workorder::getIsDelete, 0);

        if(type1Count == 0){
            workorderWrapper.set(Workorder::getCommercialAmount, null);
            workorderWrapper.set(Workorder::getUpstreamCommercialAmount, null);
            workorderWrapper.set(Workorder::getUpstreamCommercialPercentage, null);
            workorderWrapper.set(Workorder::getDownstreamCommercialAmount, null);
            workorderWrapper.set(Workorder::getDownstreamCommercialPercentage, null);
        }
        if(type3Count == 0){
            workorderWrapper.set(Workorder::getCompulsoryAmount, null);
            workorderWrapper.set(Workorder::getUpstreamCompulsoryAmount, null);
            workorderWrapper.set(Workorder::getUpstreamCompulsoryPercentage, null);
            workorderWrapper.set(Workorder::getDownstreamCompulsoryAmount, null);
            workorderWrapper.set(Workorder::getDownstreamCompulsoryPercentage, null);

            workorderWrapper.set(Workorder::getVehicleAndTaxAmount, null);
            workorderWrapper.set(Workorder::getUpstreamVehicleAndVesselTaxPercentage, null);
            workorderWrapper.set(Workorder::getUpstreamVehicleAndVesselTaxAmount, null);
            workorderWrapper.set(Workorder::getDownstreamVehicleAndVesselTaxAmount, null);
            workorderWrapper.set(Workorder::getDownstreamVehicleAndVesselTaxPercentage, null);
        }

        int x = workorderMapper.update(workorder, workorderWrapper);

        if (params.getType() == 0){
            LambdaUpdateWrapper<VehicleCertificate> certificateWrapper = new LambdaUpdateWrapper<>();
            certificateWrapper.eq(VehicleCertificate::getWorkorderId, workorder.getId());
            certificateWrapper.eq(VehicleCertificate::getIsDelete, 0);
            certificateWrapper.set(VehicleCertificate::getUpdateBy, updateBy);
            certificateWrapper.set(VehicleCertificate::getIsDelete, 1);
            vehicleCertificateMapper.update(null, certificateWrapper);

            LambdaUpdateWrapper<VehicleInvoice> invoiceWrapper = new LambdaUpdateWrapper<>();
            invoiceWrapper.eq(VehicleInvoice::getWorkorderId, workorder.getId());
            invoiceWrapper.eq(VehicleInvoice::getIsDelete, 0);
            invoiceWrapper.set(VehicleInvoice::getUpdateBy, updateBy);
            invoiceWrapper.set(VehicleInvoice::getIsDelete, 1);
            vehicleInvoiceMapper.update(null, invoiceWrapper);


            VehicleLicense vehicleLicense = params.getVehicleLicense();
            vehicleLicense.setUpdateBy(updateBy);
            LambdaQueryWrapper<VehicleLicense> licenseExistWrapper = new LambdaQueryWrapper<>();
            licenseExistWrapper.eq(VehicleLicense::getWorkorderId, workorder.getId());
            licenseExistWrapper.eq(VehicleLicense::getIsDelete, 0);
            if (vehicleLicenseMapper.selectCount(licenseExistWrapper) > 0) {
                LambdaUpdateWrapper<VehicleLicense> licenseWrapper = new LambdaUpdateWrapper<>();
                licenseWrapper.eq(VehicleLicense::getWorkorderId, workorder.getId());
                licenseWrapper.eq(VehicleLicense::getIsDelete, 0);
                vehicleLicenseMapper.update(vehicleLicense, licenseWrapper);
            }
            else {
                vehicleLicense.setWorkorderId(workorder.getId());
                vehicleLicenseMapper.insert(vehicleLicense);
            }
        }
        else {
            LambdaUpdateWrapper<VehicleLicense> licenseWrapper = new LambdaUpdateWrapper<>();
            licenseWrapper.eq(VehicleLicense::getWorkorderId, workorder.getId());
            licenseWrapper.eq(VehicleLicense::getIsDelete, 0);
            licenseWrapper.set(VehicleLicense::getUpdateBy, updateBy);
            licenseWrapper.set(VehicleLicense::getIsDelete, 1);
            vehicleLicenseMapper.update(null, licenseWrapper);


            VehicleInvoice vehicleInvoice = params.getVehicleInvoice();
            vehicleInvoice.setUpdateBy(updateBy);
            LambdaQueryWrapper<VehicleInvoice> invoiceExistWrapper = new LambdaQueryWrapper<>();
            invoiceExistWrapper.eq(VehicleInvoice::getWorkorderId, workorder.getId());
            invoiceExistWrapper.eq(VehicleInvoice::getIsDelete, 0);
            if (vehicleInvoiceMapper.selectCount(invoiceExistWrapper) > 0) {
                LambdaUpdateWrapper<VehicleInvoice> invoiceWrapper = new LambdaUpdateWrapper<>();
                invoiceWrapper.eq(VehicleInvoice::getWorkorderId, workorder.getId());
                invoiceWrapper.eq(VehicleInvoice::getIsDelete, 0);
                vehicleInvoiceMapper.update(vehicleInvoice, invoiceWrapper);
            }
            else {
                vehicleInvoice.setWorkorderId(workorder.getId());
                vehicleInvoiceMapper.insert(vehicleInvoice);
            }


            VehicleCertificate vehicleCertificate = params.getVehicleCertificate();
            vehicleCertificate.setUpdateBy(updateBy);
            LambdaQueryWrapper<VehicleCertificate> certificateExistWrapper = new LambdaQueryWrapper<>();
            certificateExistWrapper.eq(VehicleCertificate::getWorkorderId, workorder.getId());
            certificateExistWrapper.eq(VehicleCertificate::getIsDelete, 0);
            if (vehicleCertificateMapper.selectCount(certificateExistWrapper) > 0) {
                LambdaUpdateWrapper<VehicleCertificate> certificateWrapper = new LambdaUpdateWrapper<>();
                certificateWrapper.eq(VehicleCertificate::getWorkorderId, workorder.getId());
                certificateWrapper.eq(VehicleCertificate::getIsDelete, 0);
                vehicleCertificateMapper.update(vehicleCertificate, certificateWrapper);
            }
            else {
                vehicleCertificate.setWorkorderId(workorder.getId());
                vehicleCertificateMapper.insert(vehicleCertificate);
            }
        }

        // 查询已经存在的基础信息文件所对应的文件id
        LambdaQueryWrapper<WorkorderFile> workorderFileWrapper = new LambdaQueryWrapper<>();
        workorderFileWrapper.eq(WorkorderFile::getWorkorderId, workorder.getId());
        workorderFileWrapper.eq(WorkorderFile::getIsDelete, 0);
        workorderFileWrapper.in(WorkorderFile::getType, Arrays.asList("idCardFront","idCardBack","licenseFront","licenseBack","certificate","invoice", "businessLicense"));
        List<Long> existFileIds = workorderFileMapper.selectList(workorderFileWrapper).stream()
                .map(WorkorderFile::getFileId)
                .toList();

        // 将旧文件置为未关联
        LambdaUpdateWrapper<SystemFile> fileWrapper = new LambdaUpdateWrapper<>();
        if(!existFileIds.isEmpty()){
            fileWrapper.in(SystemFile::getId, existFileIds);
            fileWrapper.eq(SystemFile::getIsDelete, 0);
            fileWrapper.set(SystemFile::getUpdateBy, updateBy);
            fileWrapper.set(SystemFile::getIsLinked, 0);
            systemFileMapper.update(null, fileWrapper);
        }

        // 删除旧工单文件
        LambdaUpdateWrapper<WorkorderFile> workorderFileUpdateWrapper= new LambdaUpdateWrapper<>();
        workorderFileUpdateWrapper.eq(WorkorderFile::getWorkorderId, workorder.getId());
        workorderFileUpdateWrapper.eq(WorkorderFile::getIsDelete, 0);
        workorderFileUpdateWrapper.in(WorkorderFile::getType, Arrays.asList("idCardFront","idCardBack","licenseFront","licenseBack","certificate","invoice", "businessLicense"));
        workorderFileUpdateWrapper.set(WorkorderFile::getUpdateBy, updateBy);
        workorderFileUpdateWrapper.set(WorkorderFile::getIsDelete, 1);
        workorderFileMapper.update(null, workorderFileUpdateWrapper);

        if(params.getWorkorderFileList() != null && !params.getWorkorderFileList().isEmpty()){
            // 插入新工单文件
            List<WorkorderFile> workorderFileList = params.getWorkorderFileList();
            workorderFileList.forEach(item -> {
                item.setWorkorderId(workorder.getId());
                item.setUpdateBy(updateBy);
                item.setIsDelete(0);
            });
            workorderFileMapper.insertBatchSomeColumn(workorderFileList);

            // 更新文件为已关联
            List<Long> newFileIds = params.getWorkorderFileList().stream()
                    .map(WorkorderFile::getFileId)
                    .toList();
            fileWrapper.clear();
            fileWrapper.in(SystemFile::getId, newFileIds);
            fileWrapper.set(SystemFile::getIsLinked, 1);
            fileWrapper.set(SystemFile::getUpdateBy, updateBy);
            systemFileMapper.update(null, fileWrapper);
        }

        return new ResponseResult(200, "已更新" + x + "条数据");
    }


    @Override
    public ResponseResult delete(Long id) {
        return null;
    }

    @Override
    public ResponseResult acceptWorkorder(WorkorderDTO params) {
        Workorder workorder = new Workorder();
        workorder.setId(params.getId());
        workorder.setHandleMerchantId(params.getHandleMerchantId());
        workorder.setHandleBy(params.getHandleBy());
        workorder.setUpdateBy(SystemCommonUtil.getNowUserId());
        workorder.setStatus(params.getStatus());  // 状态
        int x = workorderMapper.updateById(workorder);
        if (x > 0){
            return new ResponseResult(200, "更新成功");
        }
        else {
            return new ResponseResult(400, "更新失败");
        }
    }

    @Override
    public ResponseResult updateQuotation(WorkorderDTO params) {
        Long updateBy = SystemCommonUtil.getNowUserId();
        Workorder workorder = new Workorder(params);
        workorder.setUpdateBy(updateBy);
        workorder.computeAmount();

        LambdaUpdateWrapper<Workorder> workorderUpdateWrapper = new LambdaUpdateWrapper<>();
        workorderUpdateWrapper.eq(Workorder::getId, workorder.getId());
        workorderUpdateWrapper.eq(Workorder::getIsDelete, 0);
        // 基础金额相关字段
        workorderUpdateWrapper.set(Workorder::getCommercialAmount, workorder.getCommercialAmount());
        workorderUpdateWrapper.set(Workorder::getCompulsoryAmount, workorder.getCompulsoryAmount());
        workorderUpdateWrapper.set(Workorder::getVehicleAndTaxAmount, workorder.getVehicleAndTaxAmount());
        workorderUpdateWrapper.set(Workorder::getNonMotorAmount, workorder.getNonMotorAmount());
        workorderUpdateWrapper.set(Workorder::getNonMotorInsuranceName, workorder.getNonMotorInsuranceName());
        workorderUpdateWrapper.set(Workorder::getNonMotorCoverageAmount, workorder.getNonMotorCoverageAmount());

        // 上游相关字段
        workorderUpdateWrapper.set(Workorder::getUpstreamComputeType, workorder.getUpstreamComputeType());
        workorderUpdateWrapper.set(Workorder::getUpstreamCommercialPercentage, workorder.getUpstreamCommercialPercentage());
        workorderUpdateWrapper.set(Workorder::getUpstreamCompulsoryPercentage, workorder.getUpstreamCompulsoryPercentage());
        workorderUpdateWrapper.set(Workorder::getUpstreamVehicleAndVesselTaxPercentage, workorder.getUpstreamVehicleAndVesselTaxPercentage());
        workorderUpdateWrapper.set(Workorder::getUpstreamNonMotorPercentage, workorder.getUpstreamNonMotorPercentage());
        workorderUpdateWrapper.set(Workorder::getUpstreamCommercialAmount, workorder.getUpstreamCommercialAmount());
        workorderUpdateWrapper.set(Workorder::getUpstreamCompulsoryAmount, workorder.getUpstreamCompulsoryAmount());
        workorderUpdateWrapper.set(Workorder::getUpstreamVehicleAndVesselTaxAmount, workorder.getUpstreamVehicleAndVesselTaxAmount());
        workorderUpdateWrapper.set(Workorder::getUpstreamNonMotorAmount, workorder.getUpstreamNonMotorAmount());

        // 下游相关字段
        workorderUpdateWrapper.set(Workorder::getDownstreamComputeType, workorder.getDownstreamComputeType());
        workorderUpdateWrapper.set(Workorder::getDownstreamCommercialPercentage, workorder.getDownstreamCommercialPercentage());
        workorderUpdateWrapper.set(Workorder::getDownstreamCompulsoryPercentage, workorder.getDownstreamCompulsoryPercentage());
        workorderUpdateWrapper.set(Workorder::getDownstreamVehicleAndVesselTaxPercentage, workorder.getDownstreamVehicleAndVesselTaxPercentage());
        workorderUpdateWrapper.set(Workorder::getDownstreamNonMotorPercentage, workorder.getDownstreamNonMotorPercentage());
        workorderUpdateWrapper.set(Workorder::getDownstreamCommercialAmount, workorder.getDownstreamCommercialAmount());
        workorderUpdateWrapper.set(Workorder::getDownstreamCompulsoryAmount, workorder.getDownstreamCompulsoryAmount());
        workorderUpdateWrapper.set(Workorder::getDownstreamVehicleAndVesselTaxAmount, workorder.getDownstreamVehicleAndVesselTaxAmount());
        workorderUpdateWrapper.set(Workorder::getDownstreamNonMotorAmount, workorder.getDownstreamNonMotorAmount());


        int x = workorderMapper.update(workorder, workorderUpdateWrapper);

        updateFile(params, List.of("quotation"));
        return new ResponseResult(200, "已更新" + x + "条数据");
    }

    @Override
    public ResponseResult updateNoCascade(WorkorderDTO params) {
        Workorder workorder = new Workorder(params);
        workorder.setUpdateBy(SystemCommonUtil.getNowUserId());
        int x = workorderMapper.updateById(workorder);
        return new ResponseResult(200, "已更新" + x + "条数据");
    }

    @Override
    public ResponseResult acceptInsurance(WorkorderDTO params) {
        Long updateBy = SystemCommonUtil.getNowUserId();
        Workorder workorder = new Workorder(params);
        workorder.setUpdateBy(updateBy);
        int x = workorderMapper.updateById(workorder);

        updateFile(params, List.of("acceptInsuranceCommercial", "acceptInsuranceCompulsory", "acceptInsuranceOther"));
        return new ResponseResult(200, "已更新" + x + "条数据");
    }

    private void updateFile(WorkorderDTO params, List<String> updateTypes){
        // 查询已存在的文件id并取消关联
        Long updateBy = SystemCommonUtil.getNowUserId();
        List<WorkorderFile> existFileList = workorderFileMapper.selectList(new LambdaQueryWrapper<WorkorderFile>()
                .eq(WorkorderFile::getWorkorderId, params.getId())
                .eq(WorkorderFile::getIsDelete, 0)
                .in(WorkorderFile::getType, updateTypes));
        List<Long> existFileIds = existFileList.stream()
                .map(WorkorderFile::getFileId)
                .toList();
        LambdaUpdateWrapper<SystemFile> fileWrapper = new LambdaUpdateWrapper<>();
        if (!existFileIds.isEmpty()){
            fileWrapper.in(SystemFile::getId, existFileIds);
            fileWrapper.eq(SystemFile::getIsDelete, 0);
            fileWrapper.set(SystemFile::getIsLinked, 0);
            fileWrapper.set(SystemFile::getUpdateBy, updateBy);
            systemFileMapper.update(null, fileWrapper);
        }

        // 删除已经存在的工单文件
        LambdaUpdateWrapper<WorkorderFile> workorderFileWrapper= new LambdaUpdateWrapper<>();
        workorderFileWrapper.eq(WorkorderFile::getWorkorderId, params.getId());
        workorderFileWrapper.eq(WorkorderFile::getIsDelete, 0);
        workorderFileWrapper.in(WorkorderFile::getType, updateTypes);
        workorderFileWrapper.set(WorkorderFile::getUpdateBy, updateBy);
        workorderFileWrapper.set(WorkorderFile::getIsDelete, 1);
        workorderFileMapper.update(null, workorderFileWrapper);

        if(params.getWorkorderFileList() != null && !params.getWorkorderFileList().isEmpty()){
            // 插入新工单文件
            List<WorkorderFile> workorderFileList = params.getWorkorderFileList();
            workorderFileList.forEach(item -> {
                item.setWorkorderId(params.getId());
                item.setUpdateBy(updateBy);
                item.setIsDelete(0);
            });
            workorderFileMapper.insertBatchSomeColumn(workorderFileList);

            // 更新文件为已关联
            List<Long> newFileIds = params.getWorkorderFileList().stream()
                    .map(WorkorderFile::getFileId)
                    .toList();
            fileWrapper.clear();
            fileWrapper.in(SystemFile::getId, newFileIds);
            fileWrapper.set(SystemFile::getIsLinked, 1);
            fileWrapper.set(SystemFile::getUpdateBy, updateBy);
            systemFileMapper.update(null, fileWrapper);
        }
    }

}
