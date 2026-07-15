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
import com.example.insurancesystem.utils.UniqueCodeRetryUtil;
import com.github.pagehelper.PageHelper;
import org.apache.commons.collections4.map.HashedMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class WorkorderServiceImpl implements WorkorderService {

    private static final int RENEWAL_REMIND_DAYS = 365;

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
    private WorkorderAggregateMapper workorderAggregateMapper;

    @Autowired
    private OSSUtil ossUtil;

    @Autowired
    private InsuranceService insuranceService;

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

        params.setRenewalRemindDays(RENEWAL_REMIND_DAYS);

        if(!SystemCommonUtil.hasPerm("all")){
            params.setCreateBy(SystemCommonUtil.getNowUserId());
        }

        List<WorkorderDTO> workorderDTOList = workorderMapper.selectRenewByWorkorderSearchDTO(params);
        TableData<WorkorderDTO> tableData = new TableData<>(workorderDTOList);
        tableData.getTable().forEach(WorkorderDTO::compute);
        return new ResponseResult(200, tableData);
    }

    @Override
    public ResponseResult selectRenewCount() {

        // 如果访问的管理员，同时获取管理员自己的快过期的保单数量和所有人的数量
        // 如果是录单员，只获取自己的快过期保单数量
        Map<String, Integer> countMap = new HashedMap<>();

        Long nowUserId = SystemCommonUtil.getNowUserId();
        if(SystemCommonUtil.hasPerm("all")){
            Integer selfCount = workorderMapper.selectRenewCount(RENEWAL_REMIND_DAYS, nowUserId);
            Integer allCount = workorderMapper.selectRenewCount(RENEWAL_REMIND_DAYS, null);

            countMap.put("selfCount", selfCount);
            countMap.put("allCount", allCount);
            return new ResponseResult(200, countMap);
        }
        else{
            Integer selfCount = workorderMapper.selectRenewCount(RENEWAL_REMIND_DAYS, nowUserId);

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
        workorder.setEnterpriseId(1L);
        if (workorder.getUpstreamComputeType() == null) workorder.setUpstreamComputeType(0);
        if (workorder.getDownstreamComputeType() == null) workorder.setDownstreamComputeType(0);
        workorder.setCreateBy(nowUserId);

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
            workorder.setPayeeStaffId(payee.getId());
            workorder.setPayName(payee.getRealName());
            workorder.setPayIdNum(payee.getUsername());
        }
        workorder.setUpdateBy(nowUserId);
        workorder.setHandleBy(nowUserId);
        workorder.setStatus(2);  // 自动接单，进入待报价阶段
        int x = UniqueCodeRetryUtil.insertWithGeneratedCode(
                UniqueCodeRetryUtil.WORKORDER_CODE_CONSTRAINT,
                workorder::setCode,
                () -> workorderMapper.insert(workorder));
        saveAggregate(workorder);

        List<WorkorderInsurance> workorderInsuranceList = params.getWorkorderInsuranceList();
        workorderInsuranceList.forEach(item -> {
            item.setWorkorderId(workorder.getId());
            item.setEnterpriseId(1L);
            item.setUpdateBy(SystemCommonUtil.getNowUserId());
            item.setIsDelete(0);
        });
        workorderInsuranceMapper.insertBatchSomeColumn(workorderInsuranceList);

        if (params.getType() == 0){
            VehicleLicense vehicleLicense = params.getVehicleLicense();
            vehicleLicense.setWorkorderId(workorder.getId());
            vehicleLicense.setEnterpriseId(1L);
            vehicleLicense.setUpdateBy(SystemCommonUtil.getNowUserId());
            vehicleLicenseMapper.insert(vehicleLicense);
        }
        else {
            VehicleCertificate vehicleCertificate = params.getVehicleCertificate();
            vehicleCertificate.setWorkorderId(workorder.getId());
            vehicleCertificate.setEnterpriseId(1L);
            vehicleCertificate.setUpdateBy(SystemCommonUtil.getNowUserId());
            vehicleCertificateMapper.insert(vehicleCertificate);

            VehicleInvoice vehicleInvoice = params.getVehicleInvoice();
            vehicleInvoice.setWorkorderId(workorder.getId());
            vehicleInvoice.setEnterpriseId(1L);
            vehicleInvoice.setUpdateBy(SystemCommonUtil.getNowUserId());
            vehicleInvoiceMapper.insert(vehicleInvoice);
        }

        List<WorkorderFile> workorderFileList = params.getWorkorderFileList();
        if(workorderFileList != null && !workorderFileList.isEmpty()){
            workorderFileList.forEach(item -> {
                item.setWorkorderId(workorder.getId());
                item.setEnterpriseId(1L);
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
        workorder.setEnterpriseId(1L);
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

        if(type1Count == 0){
            workorder.setCommercialAmount(null);
            workorder.setUpstreamCommercialAmount(null);
            workorder.setUpstreamCommercialPercentage(null);
            workorder.setDownstreamCommercialAmount(null);
            workorder.setDownstreamCommercialPercentage(null);
        }
        if(type3Count == 0){
            workorder.setCompulsoryAmount(null);
            workorder.setUpstreamCompulsoryAmount(null);
            workorder.setUpstreamCompulsoryPercentage(null);
            workorder.setDownstreamCompulsoryAmount(null);
            workorder.setDownstreamCompulsoryPercentage(null);
            workorder.setVehicleAndTaxAmount(null);
            workorder.setUpstreamVehicleAndVesselTaxPercentage(null);
            workorder.setUpstreamVehicleAndVesselTaxAmount(null);
            workorder.setDownstreamVehicleAndVesselTaxAmount(null);
            workorder.setDownstreamVehicleAndVesselTaxPercentage(null);
        }

        int x = workorderMapper.updateById(workorder);
        saveAggregate(workorder);

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
                vehicleLicense.setEnterpriseId(1L);
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
                vehicleInvoice.setEnterpriseId(1L);
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
                vehicleCertificate.setEnterpriseId(1L);
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
                item.setEnterpriseId(1L);
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
        workorder.setEnterpriseId(1L);
        workorder.setUpdateBy(updateBy);
        workorder.computeAmount();
        int x = workorderMapper.updateById(workorder);
        saveQuoteAndCommission(workorder);

        updateFile(params, List.of("quotation"));
        return new ResponseResult(200, "已更新" + x + "条数据");
    }

    @Override
    public ResponseResult updateNoCascade(WorkorderDTO params) {
        Workorder workorder = new Workorder(params);
        workorder.setEnterpriseId(1L);
        workorder.setUpdateBy(SystemCommonUtil.getNowUserId());
        int x = workorderMapper.updateById(workorder);
        if (workorder.getRemindStatus() != null) {
            workorderMapper.updateRenewalStatusCycle(workorder.getId());
        }
        if (workorder.getQuotationFailedRemark() != null) saveQuote(workorder);
        if (workorder.getRequiredPayAmount() != null || workorder.getPayFailedRemark() != null) savePayment(workorder);
        if (workorder.getUnderwritingFailedRemark() != null || workorder.getAcceptInsuranceFailedRemark() != null) saveUnderwriting(workorder);
        if (workorder.getTrackingNum() != null || workorder.getLogisticsCompany() != null) saveLogistics(workorder);
        return new ResponseResult(200, "已更新" + x + "条数据");
    }

    @Override
    public ResponseResult acceptInsurance(WorkorderDTO params) {
        Long updateBy = SystemCommonUtil.getNowUserId();
        Workorder workorder = new Workorder(params);
        workorder.setEnterpriseId(1L);
        workorder.setUpdateBy(updateBy);
        int x = workorderMapper.updateById(workorder);
        savePayment(workorder);
        saveUnderwriting(workorder);

        updateFile(params, List.of("acceptInsuranceCommercial", "acceptInsuranceCompulsory", "acceptInsuranceOther"));
        return new ResponseResult(200, "已更新" + x + "条数据");
    }

    private void saveAggregate(Workorder workorder) {
        if (workorder.getEnterpriseId() == null) workorder.setEnterpriseId(1L);
        saveQuoteAndCommission(workorder);
        savePayment(workorder);
        saveUnderwriting(workorder);
        saveLogistics(workorder);
    }

    @Override
    public ResponseResult disableRenewReminder(Long id) {
        LambdaUpdateWrapper<Workorder> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Workorder::getId, id)
                .eq(Workorder::getIsDelete, 0)
                .set(Workorder::getRenewalReminderDisabled, 1)
                .set(Workorder::getUpdateBy, SystemCommonUtil.getNowUserId());
        if (!SystemCommonUtil.hasPerm("all")) {
            wrapper.eq(Workorder::getCreateBy, SystemCommonUtil.getNowUserId());
        }
        int updated = workorderMapper.update(null, wrapper);
        if (updated == 0) return new ResponseResult(404, "工单不存在或无权操作");
        return new ResponseResult(200, "已关闭续保提醒");
    }

    private void saveQuoteAndCommission(Workorder workorder) {
        saveQuote(workorder);
        if (workorderAggregateMapper.updateCommission(workorder, "UPSTREAM") == 0) {
            workorderAggregateMapper.insertCommission(workorder, "UPSTREAM");
        }
        if (workorderAggregateMapper.updateCommission(workorder, "DOWNSTREAM") == 0) {
            workorderAggregateMapper.insertCommission(workorder, "DOWNSTREAM");
        }
    }

    private void saveQuote(Workorder workorder) {
        if (workorderAggregateMapper.updateQuote(workorder) == 0) workorderAggregateMapper.insertQuote(workorder);
    }

    private void savePayment(Workorder workorder) {
        if (workorderAggregateMapper.updatePayment(workorder) == 0) workorderAggregateMapper.insertPayment(workorder);
    }

    private void saveUnderwriting(Workorder workorder) {
        if (workorderAggregateMapper.updateUnderwriting(workorder) == 0) workorderAggregateMapper.insertUnderwriting(workorder);
    }

    private void saveLogistics(Workorder workorder) {
        if (workorderAggregateMapper.updateLogistics(workorder) == 0) workorderAggregateMapper.insertLogistics(workorder);
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
                item.setEnterpriseId(1L);
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
