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
import com.example.insurancesystem.service.WorkorderPolicyValidator;
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
import java.util.Set;
import java.util.HashSet;
import java.util.Objects;
import java.util.ArrayList;

@Service
@Transactional
/**
 * 负责车险工单从录入、报价、缴费、承保到物流和续保提醒的完整聚合生命周期。
 * 工单主体、金额表、险种、车辆证件和附件在事务中保持同步；已合并阶段不再写旧表。
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

        if(!SystemCommonUtil.hasPerm("all") && !Objects.equals(workorderDTO.getHandleBy(), SystemCommonUtil.getNowUserId())){
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
        workorderDTO.compute();

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
     * 基础资料不再清空保单金额或政策；基础资料附件仍以新提交列表整体替换。
     */
    public ResponseResult updateBaseInfo(WorkorderDTO params) {
        validateSubmitter(params.getSubmitterName());
        Workorder current = requireStageWorkorder(params);
        Long updateBy = SystemCommonUtil.getNowUserId();
        Workorder workorder = new Workorder();
        workorder.setId(current.getId()); workorder.setEnterpriseId(current.getEnterpriseId()); workorder.setUpdateBy(updateBy);
        org.springframework.beans.BeanWrapper input = new org.springframework.beans.BeanWrapperImpl(params);
        org.springframework.beans.BeanWrapper output = new org.springframework.beans.BeanWrapperImpl(workorder);
        /*
         * 基础资料白名单与保单、支付和政策隔离，防止抽屉回传旧详情覆盖新承保资料。
         * 进入待承保后起期由完整保单表单维护；此前仍允许录入续保客户的预填起期。
         */
        for (String property : List.of("type", "ownerType", "organizationName", "socialCreditCode", "createMerchantId",
                "submitterName", "handleMerchantId", "areaCode", "insuranceMerchantId", "remark", "ownerPhone", "ownerIdNum", "ownerName")) {
            output.setPropertyValue(property, input.getPropertyValue(property));
        }
        if (current.getStatus() < 8) {
            workorder.setCommercialInsuranceStartTime(params.getCommercialInsuranceStartTime());
            workorder.setCompulsoryInsuranceStartTime(params.getCompulsoryInsuranceStartTime());
            workorder.setNonMotorInsuranceStartTime(params.getNonMotorInsuranceStartTime());
        }
        List<WorkorderInsurance> workorderInsuranceList = params.getWorkorderInsuranceList();
        workorderInsuranceMapper.batchUpdateByWorkorderId(workorderInsuranceList, workorder.getId(), updateBy);

        /*
         * 基础资料只维护投保需求，不按字典选项清除保单事实及已确认政策。
         * 取消保单必须在承保表单明确提交完整险种列表，避免基础资料保存造成历史金额丢失。
         */
        int x = workorderMapper.updateById(workorder);

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


    /**
     * 报价入口仅保存报价附件、反馈和服务器报价时间，不接收保单、保费和政策字段。
     * 工单行锁保护阶段推进；编辑较早阶段时保持已有后续状态，不清空承保资料。
     * @param params 报价完整附件列表和反馈；原页面成功目标状态为 8。
     * @return 统一成功响应，权限/文件或状态校验失败时事务整体回滚。
     */
    @Override
    public ResponseResult updateQuotation(WorkorderDTO params) {
        Workorder current = requireStageWorkorder(params);
        if (params.getStatus() == null || !List.of(4, 6, 8, 10).contains(params.getStatus()))
            throw new BusinessException(400, "报价状态不正确");
        validateStageFiles(params, List.of("quotation"));
        Workorder update = new Workorder();
        update.setId(current.getId());
        update.setEnterpriseId(current.getEnterpriseId());
        update.setUpdateBy(SystemCommonUtil.getNowUserId());
        update.setQuotationRemark(WorkorderPolicyValidator.text(params.getQuotationRemark(), 500, false));
        update.setQuotationTime(System.currentTimeMillis() / 1000);
        update.setStatus(Math.max(current.getStatus(), params.getStatus() == 10 ? 8 : params.getStatus()));
        workorderAggregateMapper.updateMainStage(update);
        saveQuote(update);
        updateFile(params, List.of("quotation"));
        return new ResponseResult(200, "反馈成功");
    }

    /**
     * 原失败反馈和支付确认仍使用轻量接口，按状态白名单构建更新，避免越过承保校验。
     * 支付确认的原金额独立保存，不能写入保单应付派生字段或更改保单和政策。
     * @param params 工单 ID、目标状态与该阶段资料。
     * @return 成功响应，非法状态或字段长度/金额失败返回业务异常并回滚。
     */
    @Override
    public ResponseResult updateNoCascade(WorkorderDTO params) {
        Workorder current = requireStageWorkorder(params);
        if (params.getRemindStatus() != null || params.getStatus() == null)
            throw new BusinessException(400, "请使用对应业务入口");
        int status = params.getStatus();
        Set<Integer> allowed = switch (status) {
            case 3 -> Set.of(2, 3);
            case 5 -> Set.of(4, 5);
            case 7, 8 -> Set.of(6, 7);
            case 9 -> Set.of(8, 9);
            default -> Set.of();
        };
        if (!allowed.contains(current.getStatus())) throw new BusinessException(400, "工单状态已变化，请刷新后重试");
        Workorder update = new Workorder();
        update.setId(current.getId()); update.setEnterpriseId(current.getEnterpriseId());
        update.setUpdateBy(SystemCommonUtil.getNowUserId()); update.setStatus(status);
        if (status == 3) update.setQuotationFailedRemark(WorkorderPolicyValidator.text(params.getQuotationFailedRemark(), 500, true));
        if (status == 5) update.setUnderwritingFailedRemark(WorkorderPolicyValidator.text(params.getUnderwritingFailedRemark(), 500, true));
        if (status == 7) update.setPayFailedRemark(WorkorderPolicyValidator.text(params.getPayFailedRemark(), 500, true));
        if (status == 9) update.setAcceptInsuranceFailedRemark(WorkorderPolicyValidator.text(params.getAcceptInsuranceFailedRemark(), 500, true));
        if (status == 8) {
            WorkorderPolicyValidator.money(params.getRequiredPayAmount(), true, new java.math.BigDecimal("9999999999.99"));
            update.setRequiredPayAmount(params.getRequiredPayAmount());
            update.setPayName(WorkorderPolicyValidator.text(params.getPayName(), 100, false));
            update.setPayIdNum(WorkorderPolicyValidator.text(params.getPayIdNum(), 20, false));
            update.setPayBank(WorkorderPolicyValidator.text(params.getPayBank(), 100, false));
            update.setPayBankCardNum(WorkorderPolicyValidator.text(params.getPayBankCardNum(), 100, false));
            update.setPayRemark(WorkorderPolicyValidator.text(params.getPayRemark(), 500, false));
        }
        workorderAggregateMapper.updateMainStage(update);
        if (status == 3) saveQuote(update);
        return new ResponseResult(200, "反馈成功");
    }

    /**
     * 原子保存三类保单、金额政策和收款反馈，随后将工单置为承保完成。
     * 完整快照允许取消险种并显式清空原字段；非车险单证统一为可多选的其他单证类型。
     * 比例费用按当前保费重算，支付历史金额保持不变。
     * 已接单工单允许跳过报价、核保及支付阶段直接承保，不要求报价单或先前报价记录；
     * 金额表不存在时在同一事务补建，权限、附件归属和完整承保资料仍必须校验。
     * @param params policyTypes、各类保单字段、政策模式和本次完整附件列表。
     * @return 成功响应；失败不推进状态、不替换文件，已完成单编辑不重置完成日期。
     */
    @Override
    public ResponseResult acceptInsurance(WorkorderDTO params) {
        Workorder current = requireStageWorkorder(params);
        if (current.getStatus() > 10) throw new BusinessException(400, "工单状态异常，请刷新后重试");
        List<String> fileTypes = List.of("acceptInsuranceCommercial", "acceptInsuranceCompulsory", "acceptInsuranceNonMotor", "acceptInsuranceOther", "acceptInsuranceMisc");
        /*
         * 旧版客户端和历史记录可能仍使用 acceptInsuranceNonMotor。
         * 此处只规范化本次请求附件，新关系统一写为 acceptInsuranceOther；旧类型同时纳入整体替换范围。
         */
        if (params.getWorkorderFileList() != null) {
            params.getWorkorderFileList().stream().filter(Objects::nonNull)
                    .filter(file -> "acceptInsuranceNonMotor".equals(file.getType()))
                    .forEach(file -> file.setType("acceptInsuranceOther"));
        }
        validateStageFiles(params, fileTypes);
        Workorder update = WorkorderPolicyValidator.normalize(params);
        update.setEnterpriseId(current.getEnterpriseId()); update.setUpdateBy(SystemCommonUtil.getNowUserId());
        update.setStatus(10);
        update.setFinishTime(current.getFinishTime() == null ? System.currentTimeMillis() / 1000 : current.getFinishTime());
        workorderAggregateMapper.replacePolicies(update);
        if (workorderAggregateMapper.countQuote(update) == 0) workorderAggregateMapper.insertQuote(update);
        workorderAggregateMapper.replaceFinancials(update);
        workorderAggregateMapper.updateMainStage(update);
        updateFile(params, fileTypes);
        return new ResponseResult(200, "反馈成功");
    }

    /**
     * 在服务事务内锁定当前企业有效工单，并沿用管理员/处理人的详情访问范围。
     * @param params 客户端阶段请求，仅 ID 用于定位。
     * @return 锁定的真实工单；并发提交排队后重新校验真实状态，不接受客户端企业信息。
     */
    private Workorder requireStageWorkorder(WorkorderDTO params) {
        if (params == null || params.getId() == null) throw new BusinessException(400, "请选择工单");
        Workorder current = workorderAggregateMapper.lockWorkorder(params.getId(), EnterpriseContextHolder.requireEnterpriseId());
        if (current == null) throw new BusinessException(404, "工单不存在");
        if (!SystemCommonUtil.hasPerm("all") && !Objects.equals(current.getHandleBy(), SystemCommonUtil.getNowUserId()))
            throw new BusinessException(403, "你无权处理此工单");
        if (current.getStatus() == null || current.getStatus() < 2) throw new BusinessException(400, "工单尚未接单");
        return current;
    }

    /**
     * 校验阶段附件类型、数量及真实企业归属，并锁定系统文件，防止两个工单并发占用。
     * @param params 当前工单请求，附件为完整列表，允许空列表清除报价附件。
     * @param allowed 该阶段允许替换的附件类型；非车险单证最多六份，其他保单每类一份。
     * @throws BusinessException 文件不存在、外企业、重复、非法类型或已被其他工单引用时拒绝。
     */
    private void validateStageFiles(WorkorderDTO params, List<String> allowed) {
        List<WorkorderFile> files = params.getWorkorderFileList();
        if (files == null) throw new BusinessException(400, "请提交完整附件列表");
        Set<Long> ids = new HashSet<>();
        Map<String, Integer> counts = new java.util.HashMap<>();
        for (WorkorderFile file : files) {
            if (file == null || file.getFileId() == null || !allowed.contains(file.getType()) || !ids.add(file.getFileId()))
                throw new BusinessException(400, "附件类别或文件 ID 不合法，不能重复提交");
            int count = counts.merge(file.getType(), 1, Integer::sum);
            if (count > (Set.of("acceptInsuranceOther", "acceptInsuranceMisc").contains(file.getType()) ? 6 : 1))
                throw new BusinessException(400, "附件数量超过限制");
        }
        for (Long id : ids.stream().sorted().toList()) {
            SystemFile file = systemFileMapper.selectOne(new LambdaQueryWrapper<SystemFile>()
                    .eq(SystemFile::getId, id).eq(SystemFile::getEnterpriseId, EnterpriseContextHolder.requireEnterpriseId())
                    .eq(SystemFile::getIsDelete, 0).last("FOR UPDATE"));
            if (file == null) throw new BusinessException(400, "附件不存在或不属于本企业");
            Long otherReferences = workorderFileMapper.selectCount(new LambdaQueryWrapper<WorkorderFile>()
                    .eq(WorkorderFile::getFileId, id).eq(WorkorderFile::getIsDelete, 0).ne(WorkorderFile::getWorkorderId, params.getId()));
            if (otherReferences > 0) throw new BusinessException(400, "附件已关联其他工单，请重新上传");
        }
    }

    /**
     * 将兼容 DTO 中的字段同步到合并后的金额表、主表阶段列及物流表；缺少企业标识时从上下文补齐。
     */
    private void saveAggregate(Workorder workorder) {
        if (workorder.getEnterpriseId() == null) {
            workorder.setEnterpriseId(EnterpriseContextHolder.requireEnterpriseId());
        }
        saveQuote(workorder);
        saveMainStage(workorder);
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
     * 保存金额表的部分字段，在业务事务的工单锁下补建缺失行。
     * 不用 UPDATE 影响行数推断是否存在，避免重复提交相同内容时重复插入。
     * @param workorder 主表对应的金额字段与当前企业信息，NULL 字段保持原值。
     */
    private void saveQuote(Workorder workorder) {
        workorderAggregateMapper.lockWorkorder(workorder.getId(), workorder.getEnterpriseId());
        if (workorderAggregateMapper.countQuote(workorder) == 0) workorderAggregateMapper.insertQuote(workorder);
        workorderAggregateMapper.updateQuote(workorder);
    }

    /**
     * 新增/导入时将兼容 DTO 的支付、核保与承保阶段字段写入合并主表。
     * @param workorder 已完成企业绑定的主表实体；仅非空兼容字段写入，不覆盖其他资料。
     */
    private void saveMainStage(Workorder workorder) {
        workorderAggregateMapper.updateMainStage(workorder);
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
    private void updateFile(WorkorderDTO params, List<String> updateTypes) {
        Long enterpriseId = EnterpriseContextHolder.requireEnterpriseId();
        Long updateBy = SystemCommonUtil.getNowUserId();
        List<WorkorderFile> existing = workorderFileMapper.selectList(new LambdaQueryWrapper<WorkorderFile>()
                .eq(WorkorderFile::getWorkorderId, params.getId()).eq(WorkorderFile::getEnterpriseId, enterpriseId)
                .eq(WorkorderFile::getIsDelete, 0).in(WorkorderFile::getType, updateTypes));
        Set<Long> affected = new HashSet<>();
        existing.forEach(file -> affected.add(file.getFileId()));
        workorderFileMapper.update(null, new LambdaUpdateWrapper<WorkorderFile>()
                .eq(WorkorderFile::getWorkorderId, params.getId()).eq(WorkorderFile::getEnterpriseId, enterpriseId)
                .eq(WorkorderFile::getIsDelete, 0).in(WorkorderFile::getType, updateTypes)
                .set(WorkorderFile::getIsDelete, 1).set(WorkorderFile::getUpdateBy, updateBy));
        List<WorkorderFile> files = params.getWorkorderFileList();
        if (files != null && !files.isEmpty()) {
            List<WorkorderFile> replacements = new ArrayList<>();
            for (WorkorderFile input : files) {
                WorkorderFile relation = new WorkorderFile();
                relation.setFileId(input.getFileId()); relation.setType(input.getType());
                relation.setWorkorderId(params.getId()); relation.setEnterpriseId(enterpriseId);
                relation.setIsDelete(0); relation.setUpdateBy(updateBy);
                replacements.add(relation); affected.add(relation.getFileId());
            }
            workorderFileMapper.insertBatchSomeColumn(replacements);
        }
        /* 只有不存在任何有效工单引用时才允许成为无主文件，避免定时清理误删共享资料。 */
        for (Long id : affected.stream().filter(Objects::nonNull).sorted().toList()) {
            Long references = workorderFileMapper.selectCount(new LambdaQueryWrapper<WorkorderFile>()
                    .eq(WorkorderFile::getFileId, id).eq(WorkorderFile::getIsDelete, 0));
            systemFileMapper.update(null, new LambdaUpdateWrapper<SystemFile>()
                    .eq(SystemFile::getId, id).eq(SystemFile::getEnterpriseId, enterpriseId)
                    .set(SystemFile::getIsLinked, references > 0 ? 1 : 0).set(SystemFile::getUpdateBy, updateBy));
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
     * 新表单允许两险种分别提交不同结果；旧范围参数仍兼容，ALL 允许提前完成另一险种本轮。
     * 周期取自服务器中国日期和配置月数，后续周期提醒不受本轮处理影响。
     * @param id 当前企业内工单 ID。
     * @param request 处理范围、结果及可选备注，不接受客户端指定周期。
     * @return 成功提示；非法参数返回400，不存在或无权操作返回404。
     */
    @Override
    public ResponseResult followUpRenewal(Long id, com.example.insurancesystem.domain.workorder.RenewalFollowUpRequest request) {
        /* 新表单与旧范围参数互斥，防止同一请求给出相互矛盾的处理结果。 */
        boolean separate = request != null && (request.getCommercialStatus() != null || request.getCompulsoryStatus() != null);
        if (request == null || (separate
                ? request.getScope() != null || request.getStatus() != null
                    || (request.getCommercialStatus() != null && !java.util.Set.of(0, 1, 2).contains(request.getCommercialStatus()))
                    || (request.getCompulsoryStatus() != null && !java.util.Set.of(0, 1, 2).contains(request.getCompulsoryStatus()))
                : !java.util.Set.of("COMMERCIAL", "COMPULSORY", "ALL").contains(request.getScope() == null ? "" : request.getScope())
                    || request.getStatus() == null || (request.getStatus() != 1 && request.getStatus() != 2))
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
        boolean all = !separate && "ALL".equals(request.getScope());
        boolean commercial = separate ? request.getCommercialStatus() != null : all || "COMMERCIAL".equals(request.getScope());
        boolean compulsory = separate ? request.getCompulsoryStatus() != null : all || "COMPULSORY".equals(request.getScope());
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
            update.set(Workorder::getCommercialRenewalStatus, separate ? request.getCommercialStatus() : request.getStatus())
                    .setSql("commercial_renewal_cycle = GREATEST(COALESCE(commercial_renewal_cycle,0), " + commercialCycle + ")");
        }
        if (compulsory && compulsoryCycle > 0) {
            update.set(Workorder::getCompulsoryRenewalStatus, separate ? request.getCompulsoryStatus() : request.getStatus())
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
        if (params.getQuotationTime() != null || params.getCommercialAmount() != null || params.getCompulsoryAmount() != null || params.getVehicleAndTaxAmount() != null || params.getNonMotorAmount() != null) state = 4;
        if (params.getRequiredPayAmount() != null) state = 6;
        if (params.getUnderwritingTime() != null) state = 8;
        if (params.getFinishTime() != null || (params.getCommercialPolicyNo() != null && !params.getCommercialPolicyNo().isBlank())
                || (params.getCompulsoryPolicyNo() != null && !params.getCompulsoryPolicyNo().isBlank())
                || (params.getNonMotorPolicyNo() != null && !params.getNonMotorPolicyNo().isBlank())) state = 10;
        LambdaUpdateWrapper<Workorder> update = new LambdaUpdateWrapper<Workorder>()
                .eq(Workorder::getId, created.getId()).eq(Workorder::getEnterpriseId, EnterpriseContextHolder.requireEnterpriseId())
                .set(Workorder::getHandleBy, params.getHandleBy()).set(Workorder::getStatus, state);
        workorderMapper.update(null, update);
        return response;
    }

}
