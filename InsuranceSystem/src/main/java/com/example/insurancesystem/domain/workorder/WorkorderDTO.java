package com.example.insurancesystem.domain.workorder;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
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

    public String computeCreateMerchant(){
        return createMerchantName!=null && createMerchantCode!=null ? createMerchantName + "\n" + createMerchantCode :
                (createMerchantName != null ? createMerchantName : "") + (createMerchantCode != null ? createMerchantCode : "");
    }

    public String computeHandleMerchant() {
        return handleMerchantName!=null && handleMerchantCode!=null ? handleMerchantName + "\n" + handleMerchantCode :
                (handleMerchantName != null ? handleMerchantName : "") + (handleMerchantCode != null ? handleMerchantCode : "");
    }

    public String computeInsuranceCompany() {
        return insuranceMerchantName!=null && insuranceMerchantCode!=null ? insuranceMerchantName + "\n" + insuranceMerchantCode :
                (insuranceMerchantName != null ? insuranceMerchantName : "") + (insuranceMerchantCode != null ? insuranceMerchantCode : "");
    }

    public String computeCreateUser(){
        return createUserName!=null && createUserPhone!=null ? createUserName + "\n" + createUserPhone :
                (createUserName != null ? createUserName : "") + (createUserPhone != null ? createUserPhone : "");
    }

    public String computeHandleUser(){
        return handleUserName!=null && handleUserPhone!=null ? handleUserName + "\n" + handleUserPhone :
                (handleUserName != null ? handleUserName : "") + (handleUserPhone != null ? handleUserPhone : "");
    }

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

    public String computeFinialOwnerName() {
        if (ownerName != null && !ownerName.isEmpty()){
            return ownerName;
        }
        return organizationName;
    }
}
