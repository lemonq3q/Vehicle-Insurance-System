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
import com.example.insurancesystem.handler.exception.BusinessException;
import com.example.insurancesystem.mapper.*;
import com.example.insurancesystem.service.InsuranceService;
import com.example.insurancesystem.service.WorkorderService;
import com.example.insurancesystem.security.EnterpriseContextHolder;
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
/**
 * 负责车险工单从录入、报价、缴费、承保到物流和续保提醒的完整聚合生命周期。
 * 工单主体、险种、车辆证件、附件及拆分后的业务阶段表在事务中保持同步。
 */
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
    private WorkorderAggregateMapper workorderAggregateMapper;

    @Autowired
    private OSSUtil ossUtil;

    @Autowired
    private InsuranceService insuranceService;

    @Override
    /**
     * 分页查询工单。拥有全量权限的用户可查看企业全部工单，普通处理人只能查看分配给自己的工单。
     */
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
    /**
     * 查询到达续保周期边界的工单；普通用户仅查看自己创建的业务，管理员可查看企业全量。
     */
    public ResponseResult selectRenew(WorkorderSearchDTO params) {
        PageHelper.startPage(params.getPageNum(), params.getPageSize());


        if(!SystemCommonUtil.hasPerm("all")){
            params.setCreateBy(SystemCommonUtil.getNowUserId());
        }

        List<WorkorderDTO> workorderDTOList = workorderMapper.selectRenewByWorkorderSearchDTO(params);
        TableData<WorkorderDTO> tableData = new TableData<>(workorderDTOList);
        tableData.getTable().forEach(WorkorderDTO::compute);
        return new ResponseResult(200, tableData);
    }

    @Override
    /**
     * 统计到达续保周期的工单数量。管理员同时获得本人和全企业计数，出单员只获得本人计数。
     */
    public ResponseResult selectRenewCount() {

        // 如果访问的管理员，同时获取管理员自己的快过期的保单数量和所有人的数量
        // 如果是录单员，只获取自己的快过期保单数量
        Map<String, Integer> countMap = new HashedMap<>();

        Long nowUserId = SystemCommonUtil.getNowUserId();
        if(SystemCommonUtil.hasPerm("all")){
            Integer selfCount = workorderMapper.selectRenewCount(
                    nowUserId);
            Integer allCount = workorderMapper.selectRenewCount(
                    null);

            countMap.put("selfCount", selfCount);
            countMap.put("allCount", allCount);
            return new ResponseResult(200, countMap);
        }
        else{
            Integer selfCount = workorderMapper.selectRenewCount(
                    nowUserId);

            countMap.put("selfCount", selfCount);
            return new ResponseResult(200, countMap);
        }
    }

    @Override
    /**
     * 导出当前权限范围内的工单，并在转换 Excel 行之前计算展示金额等派生字段。
     */
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
    /**
     * 查询工单聚合详情并执行处理人权限校验，同时加载有效险种和带临时 OSS 地址的附件。
     */
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
    /**
     * 创建完整工单聚合。系统自动带出创建商户的收款资料、使用下游联系人生成提交与收款快照并自动接单，
     * 随后写入险种、对应车辆证件和附件关系，同时把新附件标记为已关联。
     */
    public ResponseResult insert(WorkorderDTO params) {
        /**
         * 每张工单都必须保存创建当时的险种选择快照。空集合无法生成合法的批量插入 SQL，且通常
         * 表示当前企业没有初始化险种配置，因此在写入工单主体前返回明确的业务错误并保持事务无变更。
         */
        List<WorkorderInsurance> workorderInsuranceList = params.getWorkorderInsuranceList();
        if (workorderInsuranceList == null || workorderInsuranceList.isEmpty()) {
            throw new BusinessException(400, "当前企业尚未配置险种，无法创建工单，请先完成险种配置");
        }
        Long nowUserId = SystemCommonUtil.getNowUserId();
        Workorder workorder = new Workorder(params);
        workorder.setEnterpriseId(EnterpriseContextHolder.requireEnterpriseId());
        if (workorder.getUpstreamComputeType() == null) workorder.setUpstreamComputeType(0);
        if (workorder.getDownstreamComputeType() == null) workorder.setDownstreamComputeType(0);
        workorder.setCreateBy(nowUserId);

        // 自动添加商户信息
        Long createMerchantId = workorder.getCreateMerchantId();
        Merchant createMerchant = merchantMapper.selectById(createMerchantId);
        if (createMerchant == null || !EnterpriseContextHolder.requireEnterpriseId().equals(createMerchant.getEnterpriseId()) || !"DOWNSTREAM".equals(createMerchant.getDirection()) || !Integer.valueOf(0).equals(createMerchant.getIsDelete())) {
            throw new BusinessException(400, "提交店铺不存在或不是有效下游");
        }
        /*
         * 提交人和收款资料在建单时形成快照。人工提交的姓名优先，未提供时使用下游联系人；
         * 录入人始终记录当前登录账号，后续联系人变更不会覆盖历史工单及收款凭据。
         */
        if (workorder.getSubmitterName() == null) workorder.setSubmitterName(createMerchant.getContact());
        validateSubmitter(workorder.getSubmitterName());
        if (workorder.getPayName() == null) workorder.setPayName(createMerchant.getContact());
        if (workorder.getPayIdNum() == null) workorder.setPayIdNum(createMerchant.getPhone());
        if (workorder.getPayBank() == null) workorder.setPayBank(createMerchant.getBank());
        if (workorder.getPayBankCardNum() == null) workorder.setPayBankCardNum(createMerchant.getBankCardNum());
        workorder.setUpdateBy(nowUserId);
        workorder.setHandleBy(nowUserId);
        workorder.setStatus(2);  // 自动接单，进入待报价阶段
        int x = UniqueCodeRetryUtil.insertWithGeneratedCode(
                UniqueCodeRetryUtil.WORKORDER_CODE_CONSTRAINT,
                workorder::setCode,
                () -> workorderMapper.insert(workorder));
        saveAggregate(workorder);

        workorderInsuranceList.forEach(item -> {
            item.setWorkorderId(workorder.getId());
            item.setEnterpriseId(EnterpriseContextHolder.requireEnterpriseId());
            item.setUpdateBy(SystemCommonUtil.getNowUserId());
            item.setIsDelete(0);
        });
        workorderInsuranceMapper.insertBatchSomeColumn(workorderInsuranceList);

        if (params.getType() == 0){
            VehicleLicense vehicleLicense = params.getVehicleLicense();
            vehicleLicense.setWorkorderId(workorder.getId());
            vehicleLicense.setEnterpriseId(EnterpriseContextHolder.requireEnterpriseId());
            vehicleLicense.setUpdateBy(SystemCommonUtil.getNowUserId());
            vehicleLicenseMapper.insert(vehicleLicense);
        }
        else {
            VehicleCertificate vehicleCertificate = params.getVehicleCertificate();
            vehicleCertificate.setWorkorderId(workorder.getId());
            vehicleCertificate.setEnterpriseId(EnterpriseContextHolder.requireEnterpriseId());
            vehicleCertificate.setUpdateBy(SystemCommonUtil.getNowUserId());
            vehicleCertificateMapper.insert(vehicleCertificate);

            VehicleInvoice vehicleInvoice = params.getVehicleInvoice();
            vehicleInvoice.setWorkorderId(workorder.getId());
            vehicleInvoice.setEnterpriseId(EnterpriseContextHolder.requireEnterpriseId());
            vehicleInvoice.setUpdateBy(SystemCommonUtil.getNowUserId());
            vehicleInvoiceMapper.insert(vehicleInvoice);
        }

        List<WorkorderFile> workorderFileList = params.getWorkorderFileList();
        if(workorderFileList != null && !workorderFileList.isEmpty()){
            workorderFileList.forEach(item -> {
                item.setWorkorderId(workorder.getId());
                item.setEnterpriseId(EnterpriseContextHolder.requireEnterpriseId());
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
    /**
     * 更新工单基础资料、险种和车辆证件。车辆类型切换时逻辑删除另一类型证件，
     * 未投保的商业险或交强险会清空对应金额；基础资料附件则以新提交列表整体替换。
     */
    public ResponseResult updateBaseInfo(WorkorderDTO params) {
        validateSubmitter(params.getSubmitterName());
        Long updateBy = SystemCommonUtil.getNowUserId();
        Workorder workorder = new Workorder(params);
        workorder.setCreateBy(null);
        workorder.setEnterpriseId(EnterpriseContextHolder.requireEnterpriseId());
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
                vehicleLicense.setEnterpriseId(EnterpriseContextHolder.requireEnterpriseId());
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
                vehicleInvoice.setEnterpriseId(EnterpriseContextHolder.requireEnterpriseId());
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
                vehicleCertificate.setEnterpriseId(EnterpriseContextHolder.requireEnterpriseId());
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
                item.setEnterpriseId(EnterpriseContextHolder.requireEnterpriseId());
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
    /**
     * 工单删除接口的预留实现；当前未执行任何数据删除，由调用方收到空结果。
     */
    public ResponseResult delete(Long id) {
        return null;
    }

    /**
     * 接单、派单和续保重新分配共用入口。处理人必须是当前企业中启用的系统成员，
     * 不再依赖商户人员；已有历史记录仅在本次明确重新分配时更新。
     * 参数中的工单ID、处理人ID必填，处理机构和流程状态可选；跨企业或停用引用拒绝保存。
     * 更新条件再次限定企业和未删除工单，保留原录入人及未提交的机构、流程状态。
     */
    @Override
    public ResponseResult acceptWorkorder(WorkorderDTO params) {
        if (params == null || params.getId() == null || params.getHandleBy() == null) {
            throw new BusinessException(400, "请选择工单和本企业处理人");
        }
        Long enterpriseId = EnterpriseContextHolder.requireEnterpriseId();
        User handler = userMapper.selectSsoUser(params.getHandleBy(), enterpriseId);
        if (handler == null || !Integer.valueOf(1).equals(handler.getStatus())
                || !Integer.valueOf(1).equals(handler.getMemberStatus())) {
            throw new BusinessException(400, "处理人必须是本企业启用的系统用户");
        }
        /* 机构和人员独立校验，避免通过请求体将工单关联到其他企业的机构。 */
        if (params.getHandleMerchantId() != null) {
            Merchant merchant = merchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
                    .eq(Merchant::getId, params.getHandleMerchantId())
                    .eq(Merchant::getEnterpriseId, enterpriseId).eq(Merchant::getIsDelete, 0));
            if (merchant == null) throw new BusinessException(400, "处理机构不属于当前企业或已失效");
        }
        LambdaUpdateWrapper<Workorder> update = new LambdaUpdateWrapper<Workorder>()
                .eq(Workorder::getId, params.getId()).eq(Workorder::getEnterpriseId, enterpriseId)
                .eq(Workorder::getIsDelete, 0)
                .set(Workorder::getHandleBy, params.getHandleBy())
                .set(Workorder::getUpdateBy, SystemCommonUtil.getNowUserId())
                .set(params.getHandleMerchantId() != null, Workorder::getHandleMerchantId, params.getHandleMerchantId())
                .set(params.getStatus() != null, Workorder::getStatus, params.getStatus());
        int changed = workorderMapper.update(null, update);
        if (changed == 0) throw new BusinessException(404, "工单不存在或不属于当前企业");
        return new ResponseResult(200, "更新成功");
    }


    @Override
    /**
     * 保存报价和佣金数据，先计算金额派生值，再同步报价阶段聚合表并替换报价附件。
     */
    public ResponseResult updateQuotation(WorkorderDTO params) {
        Long updateBy = SystemCommonUtil.getNowUserId();
        Workorder workorder = new Workorder(params);
        workorder.setCreateBy(null);
        workorder.setEnterpriseId(EnterpriseContextHolder.requireEnterpriseId());
        workorder.setUpdateBy(updateBy);
        workorder.computeAmount();
        int x = workorderMapper.updateById(workorder);
        saveQuoteAndCommission(workorder);

        updateFile(params, List.of("quotation"));
        return new ResponseResult(200, "已更新" + x + "条数据");
    }

    @Override
    /**
     * 执行无需级联车辆基础资料的流程字段更新，并根据实际提交字段同步续保、报价、
     * 缴费、承保或物流阶段表，支持不同步骤复用同一个轻量更新接口。
     */
    public ResponseResult updateNoCascade(WorkorderDTO params) {
        if (params.getRemindStatus() != null) throw new BusinessException(400, "请按险种范围提交续保跟进");
        Workorder workorder = new Workorder(params);
        workorder.setCreateBy(null);
        workorder.setEnterpriseId(EnterpriseContextHolder.requireEnterpriseId());
        workorder.setUpdateBy(SystemCommonUtil.getNowUserId());
        int x = workorderMapper.updateById(workorder);
        if (workorder.getQuotationFailedRemark() != null) saveQuote(workorder);
        if (workorder.getRequiredPayAmount() != null || workorder.getPayFailedRemark() != null) savePayment(workorder);
        if (workorder.getUnderwritingFailedRemark() != null || workorder.getAcceptInsuranceFailedRemark() != null) saveUnderwriting(workorder);
        if (workorder.getTrackingNum() != null || workorder.getLogisticsCompany() != null) saveLogistics(workorder);
        return new ResponseResult(200, "已更新" + x + "条数据");
    }

    @Override
    /**
     * 保存承保结果，同步缴费与核保阶段聚合数据，并整体替换商业险、交强险等承保附件。
     */
    public ResponseResult acceptInsurance(WorkorderDTO params) {
        Long updateBy = SystemCommonUtil.getNowUserId();
        Workorder workorder = new Workorder(params);
        workorder.setCreateBy(null);
        workorder.setEnterpriseId(EnterpriseContextHolder.requireEnterpriseId());
        workorder.setUpdateBy(updateBy);
        int x = workorderMapper.updateById(workorder);
        savePayment(workorder);
        saveUnderwriting(workorder);

        updateFile(params, List.of("acceptInsuranceCommercial", "acceptInsuranceCompulsory", "acceptInsuranceOther"));
        return new ResponseResult(200, "已更新" + x + "条数据");
    }

    /**
     * 将工单主体中的阶段字段同步到报价佣金、缴费、核保及物流聚合表；缺少企业标识时从上下文补齐。
     */
    private void saveAggregate(Workorder workorder) {
        if (workorder.getEnterpriseId() == null) {
            workorder.setEnterpriseId(EnterpriseContextHolder.requireEnterpriseId());
        }
        saveQuoteAndCommission(workorder);
        savePayment(workorder);
        saveUnderwriting(workorder);
        saveLogistics(workorder);
    }

    @Override
    /**
     * 关闭单个工单的续保提醒。普通用户只能关闭自己创建的工单，管理员不受该限制。
     */
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

    /**
     * 以更新优先、无记录则插入的方式保存报价以及上下游两侧佣金，兼容历史工单尚无拆分记录的情况。
     */
    private void saveQuoteAndCommission(Workorder workorder) {
        saveQuote(workorder);
        if (workorderAggregateMapper.updateCommission(workorder, "UPSTREAM") == 0) {
            workorderAggregateMapper.insertCommission(workorder, "UPSTREAM");
        }
        if (workorderAggregateMapper.updateCommission(workorder, "DOWNSTREAM") == 0) {
            workorderAggregateMapper.insertCommission(workorder, "DOWNSTREAM");
        }
    }

    /**
     * 保存报价阶段数据；旧工单不存在报价记录时自动补建。
     */
    private void saveQuote(Workorder workorder) {
        if (workorderAggregateMapper.updateQuote(workorder) == 0) workorderAggregateMapper.insertQuote(workorder);
    }

    /**
     * 保存缴费阶段数据；旧工单不存在缴费记录时自动补建。
     */
    private void savePayment(Workorder workorder) {
        if (workorderAggregateMapper.updatePayment(workorder) == 0) workorderAggregateMapper.insertPayment(workorder);
    }

    /**
     * 保存核保与承保阶段数据；旧工单不存在记录时自动补建。
     */
    private void saveUnderwriting(Workorder workorder) {
        if (workorderAggregateMapper.updateUnderwriting(workorder) == 0) workorderAggregateMapper.insertUnderwriting(workorder);
    }

    /**
     * 保存保单寄送物流数据；旧工单不存在物流记录时自动补建。
     */
    private void saveLogistics(Workorder workorder) {
        if (workorderAggregateMapper.updateLogistics(workorder) == 0) workorderAggregateMapper.insertLogistics(workorder);
    }

    /**
     * 按业务附件类型整体替换工单附件：先解除旧系统文件关联并逻辑删除旧关系，
     * 再插入新关系、标记新文件已关联，保证临时上传文件的清理状态准确。
     */
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
                item.setEnterpriseId(EnterpriseContextHolder.requireEnterpriseId());
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

    /**
     * 限制直接录入的提交人快照长度；空字符串允许表示暂无提交人，不推导或修改系统录入人。
     * @param name 来自工单表单或下游联系人的姓名。
     * @throws BusinessException 姓名超过存储长度时在保存前终止。
     */
    private void validateSubmitter(String name) {
        if (name != null && name.length() > 100) throw new BusinessException(400, "提交人最多100字");
    }

    /**
     * 按险种原子保存续保跟进，只更新用户选择的状态，避免同时处理另一险种时覆盖对方结果。
     * 周期取自服务器中国日期；ALL 允许提前完成另一险种本轮，仍保留未来年度提醒。
     * @param id 当前企业内工单 ID。
     * @param request 处理范围、结果及可选备注，不接受客户端指定周期。
     * @return 成功提示；非法参数返回400，不存在或无权操作返回404。
     */
    @Override
    public ResponseResult followUpRenewal(Long id, com.example.insurancesystem.domain.workorder.RenewalFollowUpRequest request) {
        if (request == null || !java.util.Set.of("COMMERCIAL", "COMPULSORY", "ALL").contains(request.getScope() == null ? "" : request.getScope())
                || request.getStatus() == null || (request.getStatus() != 1 && request.getStatus() != 2)
                || (request.getFollowUpRes() != null && request.getFollowUpRes().length() > 1000)) {
            return new ResponseResult(400, "续保处理范围、结果或备注不合法");
        }
        Long enterpriseId = EnterpriseContextHolder.requireEnterpriseId();
        Workorder current = workorderMapper.selectOne(new LambdaQueryWrapper<Workorder>()
                .eq(Workorder::getId, id).eq(Workorder::getEnterpriseId, enterpriseId).eq(Workorder::getIsDelete, 0));
        Long userId = SystemCommonUtil.getNowUserId();
        if (current == null || (!SystemCommonUtil.hasPerm("all") && !userId.equals(current.getCreateBy()))) {
            return new ResponseResult(404, "工单不存在或无权操作");
        }
        java.time.LocalDate today = java.time.LocalDate.now(com.example.insurancesystem.utils.RenewalCalendar.BUSINESS_ZONE);
        boolean all = "ALL".equals(request.getScope());
        boolean commercial = all || "COMMERCIAL".equals(request.getScope());
        boolean compulsory = all || "COMPULSORY".equals(request.getScope());
        java.time.LocalDate commercialStart = com.example.insurancesystem.utils.RenewalCalendar.startDate(current.getCommercialInsuranceStartTime());
        java.time.LocalDate compulsoryStart = com.example.insurancesystem.utils.RenewalCalendar.startDate(current.getCompulsoryInsuranceStartTime());
        int commercialCycle = all ? com.example.insurancesystem.utils.RenewalCalendar.upcomingCycle(commercialStart, today)
                : com.example.insurancesystem.utils.RenewalCalendar.activeCycle(commercialStart, today);
        int compulsoryCycle = all ? com.example.insurancesystem.utils.RenewalCalendar.upcomingCycle(compulsoryStart, today)
                : com.example.insurancesystem.utils.RenewalCalendar.activeCycle(compulsoryStart, today);
        if ((!all && ((commercial && commercialCycle == 0) || (compulsory && compulsoryCycle == 0)))
                || (all && commercialCycle == 0 && compulsoryCycle == 0)) {
            return new ResponseResult(400, "所选险种无起期或尚未进入提醒周期");
        }
        LambdaUpdateWrapper<Workorder> update = new LambdaUpdateWrapper<Workorder>()
                .eq(Workorder::getId, id).eq(Workorder::getEnterpriseId, enterpriseId)
                .eq(Workorder::getIsDelete, 0).eq(Workorder::getRenewalReminderDisabled, 0)
                .set(Workorder::getUpdateBy, userId);
        /* 完成周期只能向前推进，重复处理不回退已经提前完成的另一轮状态。 */
        if (commercial && commercialCycle > 0) {
            update.set(Workorder::getCommercialRenewalStatus, request.getStatus())
                    .setSql("commercial_renewal_cycle = GREATEST(COALESCE(commercial_renewal_cycle,0), " + commercialCycle + ")");
        }
        if (compulsory && compulsoryCycle > 0) {
            update.set(Workorder::getCompulsoryRenewalStatus, request.getStatus())
                    .setSql("compulsory_renewal_cycle = GREATEST(COALESCE(compulsory_renewal_cycle,0), " + compulsoryCycle + ")");
        }
        if (request.getFollowUpRes() != null) update.set(Workorder::getFollowUpRes, request.getFollowUpRes());
        return workorderMapper.update(null, update) > 0 ? new ResponseResult(200, "已保存续保跟进")
                : new ResponseResult(400, "工单提醒已关闭或状态已变化，请刷新");
    }

    /**
     * 创建历史导入工单，复用全部正常聚合写入及业务编号生成后，仅恢复历史阶段与指定处理人。
     * @param params 已通过模板、字段、机构、成员和险种校验的完整模型。
     * @return 新工单响应；任一写入异常在调用方的独立行事务内整体回滚。
     */
    @Override
    public ResponseResult insertImported(WorkorderDTO params) {
        ResponseResult response = insert(params);
        if (response.getCode() != 200) throw new BusinessException(response.getCode(), response.getMsg());
        Workorder created = (Workorder) response.getData();
        int state = 2;
        /* 以已填写的业务阶段恢复状态，不让已经承保的历史单重新停留在待报价。 */
        if (params.getQuotationTime() != null || params.getCommercialAmount() != null || params.getCompulsoryAmount() != null || params.getVehicleAndTaxAmount() != null) state = 4;
        if (params.getRequiredPayAmount() != null) state = 6;
        if (params.getUnderwritingTime() != null) state = 8;
        if (params.getFinishTime() != null || (params.getCommercialPolicyNo() != null && !params.getCommercialPolicyNo().isBlank())
                || (params.getCompulsoryPolicyNo() != null && !params.getCompulsoryPolicyNo().isBlank())) state = 10;
        LambdaUpdateWrapper<Workorder> update = new LambdaUpdateWrapper<Workorder>()
                .eq(Workorder::getId, created.getId()).eq(Workorder::getEnterpriseId, EnterpriseContextHolder.requireEnterpriseId())
                .set(Workorder::getHandleBy, params.getHandleBy()).set(Workorder::getStatus, state);
        workorderMapper.update(null, update);
        return response;
    }

}
