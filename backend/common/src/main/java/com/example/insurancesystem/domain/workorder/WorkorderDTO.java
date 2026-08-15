package com.example.insurancesystem.domain.workorder;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
/**
 * 工单详情传输对象，聚合主工单与证件、保险、文件、支付和流转阶段信息供前端一次性展示。
 */
public class WorkorderDTO {
    private Long id;

    private String code;

    private Integer type;

    private Integer ownerType;

    private String organizationName;

    private String socialCreditCode;

    private Long createMerchantId;

    private String createMerchantName;

    private String createMerchantCode;

    private Long handleMerchantId;

    private String handleMerchantName;

    private String handleMerchantCode;

    private String areaCode;

    private Long commercialInsuranceStartTime;

    private Long compulsoryInsuranceStartTime;

    private Long insuranceMerchantId;

    private String insuranceMerchantName;

    private String insuranceMerchantCode;

    private String remark;

    private String ownerPhone;

    private String ownerIdNum;

    private String ownerName;

    private String quotationNo;

    private BigDecimal commercialAmount;

    private BigDecimal compulsoryAmount;

    private BigDecimal vehicleAndTaxAmount;

    private BigDecimal nonMotorAmount;

    private String nonMotorInsuranceName;

    private BigDecimal nonMotorCoverageAmount;

    private Integer upstreamComputeType;

    private BigDecimal upstreamCommercialPercentage;

    private BigDecimal upstreamCompulsoryPercentage;

    private BigDecimal upstreamVehicleAndVesselTaxPercentage;

    private BigDecimal upstreamNonMotorPercentage;

    private BigDecimal upstreamCommercialAmount;

    private BigDecimal upstreamCompulsoryAmount;

    private BigDecimal upstreamVehicleAndVesselTaxAmount;

    private BigDecimal upstreamNonMotorAmount;

    private Integer downstreamComputeType;

    private BigDecimal downstreamCommercialPercentage;

    private BigDecimal downstreamCompulsoryPercentage;

    private BigDecimal downstreamVehicleAndVesselTaxPercentage;

    private BigDecimal downstreamNonMotorPercentage;

    private BigDecimal downstreamCommercialAmount;

    private BigDecimal downstreamCompulsoryAmount;

    private BigDecimal downstreamVehicleAndVesselTaxAmount;

    private BigDecimal downstreamNonMotorAmount;

    private String quotationRemark;

    private String quotationFailedRemark;

    private BigDecimal requiredPayAmount;

    private String payName;

    private String payIdNum;

    private String payBank;

    private String payBankCardNum;

    private String payRemark;

    private String payFailedRemark;

    private String underwritingRemark;

    private String underwritingFailedRemark;

    private String commercialPolicyNo;

    private String compulsoryPolicyNo;

    private String trackingNum;

    private String logisticsCompany;

    private String acceptInsuranceRemark;

    private String acceptInsuranceFailedRemark;

    private Long createTime;

    private Long updateTime;

    private Long quotationTime;

    private Long underwritingTime;

    private Long finishTime;

    private Long createBy;

    private String createUserName;

    private String createUserPhone;

    private Long handleBy;

    private String handleUserName;

    private String handleUserPhone;

    private Integer status;

    private Integer remindStatus;

    private Integer renewalStatusCycle;

    private Integer renewalReminderDisabled;

    private String followUpRes;

    private VehicleLicense vehicleLicense;

    private VehicleCertificate vehicleCertificate;

    private VehicleInvoice vehicleInvoice;

    private List<WorkorderFile> workorderFileList;

    private List<WorkorderInsurance> workorderInsuranceList;

    private String vehicleId;

    private String createMerchant;

    private String handleMerchant;

    private String insuranceCompany;

    private String createUser;

    private String handleUser;

    private String workorderStatus;

    private BigDecimal upstreamSumAmount;

    private BigDecimal downstreamSumAmount;

    private String finialOwnerName;

    /**
     * 计算工单列表和详情展示所需的全部派生字段，包括车辆标识、上下游机构、经办人、状态、费用合计和最终车主名称。
     */
    public void compute(){
        this.vehicleId = computeVehicleId();
        this.createMerchant = computeCreateMerchant();
        this.handleMerchant = computeHandleMerchant();
        this.insuranceCompany = computeInsuranceCompany();
        this.createUser = computeCreateUser();
        this.handleUser = computeHandleUser();
        this.workorderStatus = computeWorkorderStatus();
        this.upstreamSumAmount = computeUpstreamSumAmount();
        this.downstreamSumAmount = computeDownstreamSumAmount();
        this.finialOwnerName = computeFinialOwnerName();
    }

    /**
     * 根据查询是否拆分关联信息决定计算范围。拆分展示时生成所有组合文本；非拆分场景只计算状态、金额和车主等公共字段，
     * 避免不必要的字符串加工。
     */
    public void compute(boolean isSplit){
        if(isSplit){
            compute();
        }
        else{
            this.workorderStatus = computeWorkorderStatus();
            this.upstreamSumAmount = computeUpstreamSumAmount();
            this.downstreamSumAmount = computeDownstreamSumAmount();
            this.finialOwnerName = computeFinialOwnerName();
        }
    }

    /**
     * 优先使用行驶证车架号，缺失时回退到合格证车架号，并与车牌号用换行组合供表格双行展示。
     */
    public String computeVehicleId(){
        String licensePlate = null;
        String vlVehicleCode = null;
        String vcVehicleCode = null;
        if (this.getVehicleLicense() != null){
            licensePlate = this.getVehicleLicense().getLicensePlate();
            vlVehicleCode = this.getVehicleLicense().getVehicleCode();
        }
        if (this.getVehicleCertificate() != null){
            vcVehicleCode = this.getVehicleCertificate().getVehicleCode();
        }
        String vehicleCode = vlVehicleCode != null ? vlVehicleCode : vcVehicleCode;
        return licensePlate !=null && vehicleCode !=null ? licensePlate + "\n" + vehicleCode :
                (licensePlate != null ? licensePlate : "") + (vehicleCode != null ? vehicleCode : "");
    }

    /**
     * 将工单创建机构名称和编码组合为双行展示文本，任一字段缺失时只返回已有值。
     */
    public String computeCreateMerchant(){
        return createMerchantName!=null && createMerchantCode!=null ? createMerchantName + "\n" + createMerchantCode :
                (createMerchantName != null ? createMerchantName : "") + (createMerchantCode != null ? createMerchantCode : "");
    }

    /**
     * 将当前处理机构名称和编码组合为双行展示文本。
     */
    public String computeHandleMerchant() {
        return handleMerchantName!=null && handleMerchantCode!=null ? handleMerchantName + "\n" + handleMerchantCode :
                (handleMerchantName != null ? handleMerchantName : "") + (handleMerchantCode != null ? handleMerchantCode : "");
    }

    /**
     * 将承保公司名称和机构编码组合为双行展示文本。
     */
    public String computeInsuranceCompany() {
        return insuranceMerchantName!=null && insuranceMerchantCode!=null ? insuranceMerchantName + "\n" + insuranceMerchantCode :
                (insuranceMerchantName != null ? insuranceMerchantName : "") + (insuranceMerchantCode != null ? insuranceMerchantCode : "");
    }

    /**
     * 将创建人姓名和手机号组合为双行展示文本，便于列表直接联系业务发起人。
     */
    public String computeCreateUser(){
        return createUserName!=null && createUserPhone!=null ? createUserName + "\n" + createUserPhone :
                (createUserName != null ? createUserName : "") + (createUserPhone != null ? createUserPhone : "");
    }

    /**
     * 将当前处理人姓名和手机号组合为双行展示文本。
     */
    public String computeHandleUser(){
        return handleUserName!=null && handleUserPhone!=null ? handleUserName + "\n" + handleUserPhone :
                (handleUserName != null ? handleUserName : "") + (handleUserPhone != null ? handleUserPhone : "");
    }

    /**
     * 将工单流程状态数值映射为待处理、报价、核保、支付、承保和完成等中文阶段；
     * 当前已知失败状态分别保留独立文案，其他状态回退为已完成。
     */
    public String computeWorkorderStatus(){
        if (status == 1){
            return "待处理";
        }
        else if (status == 2){
            return "待报价";
        }
        else if (status == 3){
            return "报价失败";
        }
        else if (status == 4){
            return "待核保";
        }
        else if (status == 5){
            return "核保失败";
        }
        else if (status == 6){
            return "支付待确认";
        }
        else if (status == 7){
            return "支付失败";
        }
        else if (status == 8){
            return "待承保";
        }
        else if ( status == 9){
            return "承保失败";
        }
        else {
            return "已完成";
        }
    }

    /**
     * 汇总上游商业险、交强险、车船税和非车险费用；所有分项缺失或合计为零时返回 null，
     * 使前端区分未产生费用与明确的零金额。
     */
    public BigDecimal computeUpstreamSumAmount() {
        BigDecimal sum = new BigDecimal(0);
        if (upstreamCommercialAmount != null){
            sum = sum.add(upstreamCommercialAmount);
        }
        if (upstreamCompulsoryAmount != null){
            sum = sum.add(upstreamCompulsoryAmount);
        }
        if (upstreamVehicleAndVesselTaxAmount != null){
            sum = sum.add(upstreamVehicleAndVesselTaxAmount);
        }
        if (upstreamNonMotorAmount != null){
            sum = sum.add(upstreamNonMotorAmount);
        }
        if (sum.compareTo(BigDecimal.ZERO) == 0){
            sum = null;
        }
        return sum;
    }

    /**
     * 汇总下游商业险、交强险、车船税和非车险费用；合计为零时保持 null 语义。
     */
    public BigDecimal computeDownstreamSumAmount() {
        BigDecimal sum = new BigDecimal(0);
        if (downstreamCommercialAmount != null){
            sum = sum.add(downstreamCommercialAmount);
        }
        if (downstreamCompulsoryAmount != null){
            sum = sum.add(downstreamCompulsoryAmount);
        }
        if (downstreamVehicleAndVesselTaxAmount != null){
            sum = sum.add(downstreamVehicleAndVesselTaxAmount);
        }
        if (downstreamNonMotorAmount != null){
            sum = sum.add(downstreamNonMotorAmount);
        }
        if (sum.compareTo(BigDecimal.ZERO) == 0){
            sum = null;
        }
        return sum;
    }

    /**
     * 最终车主优先使用个人车主姓名，缺失时回退到营业执照组织名称，兼容个人车和企业车。
     */
    public String computeFinialOwnerName() {
        if (ownerName != null && !ownerName.isEmpty()){
            return ownerName;
        }
        return organizationName;
    }
}
