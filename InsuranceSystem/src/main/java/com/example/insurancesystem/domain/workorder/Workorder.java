package com.example.insurancesystem.domain.workorder;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
@TableName(value = "workorder")
@AllArgsConstructor
@NoArgsConstructor
public class Workorder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private Integer type;

    private Integer ownerType;

    private String organizationName;

    private String socialCreditCode;

    private Long createMerchantId;

    private Long handleMerchantId;

    private String areaCode;

    private Long commercialInsuranceStartTime;

    private Long compulsoryInsuranceStartTime;

    private Long insuranceMerchantId;

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

    private Long updateBy;

    private Long handleBy;

    private Integer status;

    private Integer remindStatus;

    private String followUpRes;

    private Integer isDelete;

    public Workorder(WorkorderDTO workorderDTO){
        this.id = workorderDTO.getId();
        this.code = workorderDTO.getCode();
        this.type = workorderDTO.getType();
        this.ownerType = workorderDTO.getOwnerType();
        this.organizationName = workorderDTO.getOrganizationName();
        this.socialCreditCode = workorderDTO.getSocialCreditCode();
        this.createMerchantId = workorderDTO.getCreateMerchantId();
        this.handleMerchantId = workorderDTO.getHandleMerchantId();
        this.areaCode = workorderDTO.getAreaCode();
        this.commercialInsuranceStartTime = workorderDTO.getCommercialInsuranceStartTime();
        this.compulsoryInsuranceStartTime = workorderDTO.getCompulsoryInsuranceStartTime();
        this.insuranceMerchantId = workorderDTO.getInsuranceMerchantId();
        this.remark = workorderDTO.getRemark();
        this.ownerPhone = workorderDTO.getOwnerPhone();
        this.ownerIdNum = workorderDTO.getOwnerIdNum();
        this.ownerName = workorderDTO.getOwnerName();
        this.quotationNo = workorderDTO.getQuotationNo();
        this.commercialAmount = workorderDTO.getCommercialAmount();
        this.compulsoryAmount = workorderDTO.getCompulsoryAmount();
        this.vehicleAndTaxAmount = workorderDTO.getVehicleAndTaxAmount();
        this.nonMotorAmount = workorderDTO.getNonMotorAmount();
        this.nonMotorInsuranceName = workorderDTO.getNonMotorInsuranceName();
        this.nonMotorCoverageAmount = workorderDTO.getNonMotorCoverageAmount();
        this.upstreamComputeType = workorderDTO.getUpstreamComputeType();
        this.upstreamCommercialPercentage = workorderDTO.getUpstreamCommercialPercentage();
        this.upstreamCompulsoryPercentage = workorderDTO.getUpstreamCompulsoryPercentage();
        this.upstreamVehicleAndVesselTaxPercentage = workorderDTO.getUpstreamVehicleAndVesselTaxPercentage();
        this.upstreamNonMotorPercentage = workorderDTO.getUpstreamNonMotorPercentage();
        this.upstreamCommercialAmount = workorderDTO.getUpstreamCommercialAmount();
        this.upstreamCompulsoryAmount = workorderDTO.getUpstreamCompulsoryAmount();
        this.upstreamVehicleAndVesselTaxAmount = workorderDTO.getUpstreamVehicleAndVesselTaxAmount();
        this.upstreamNonMotorAmount = workorderDTO.getUpstreamNonMotorAmount();
        this.downstreamComputeType = workorderDTO.getDownstreamComputeType();
        this.downstreamCommercialPercentage = workorderDTO.getDownstreamCommercialPercentage();
        this.downstreamCompulsoryPercentage = workorderDTO.getDownstreamCompulsoryPercentage();
        this.downstreamVehicleAndVesselTaxPercentage = workorderDTO.getDownstreamVehicleAndVesselTaxPercentage();
        this.downstreamNonMotorPercentage = workorderDTO.getDownstreamNonMotorPercentage();
        this.downstreamCommercialAmount = workorderDTO.getDownstreamCommercialAmount();
        this.downstreamCompulsoryAmount = workorderDTO.getDownstreamCompulsoryAmount();
        this.downstreamVehicleAndVesselTaxAmount = workorderDTO.getDownstreamVehicleAndVesselTaxAmount();
        this.downstreamNonMotorAmount = workorderDTO.getDownstreamNonMotorAmount();
        this.quotationRemark = workorderDTO.getQuotationRemark();
        this.quotationFailedRemark = workorderDTO.getQuotationFailedRemark();
        this.requiredPayAmount = workorderDTO.getRequiredPayAmount();
        this.payName = workorderDTO.getPayName();
        this.payIdNum = workorderDTO.getPayIdNum();
        this.payBank = workorderDTO.getPayBank();
        this.payBankCardNum = workorderDTO.getPayBankCardNum();
        this.payRemark = workorderDTO.getPayRemark();
        this.payFailedRemark = workorderDTO.getPayFailedRemark();
        this.underwritingRemark = workorderDTO.getUnderwritingRemark();
        this.underwritingFailedRemark = workorderDTO.getUnderwritingFailedRemark();
        this.commercialPolicyNo = workorderDTO.getCommercialPolicyNo();
        this.compulsoryPolicyNo = workorderDTO.getCompulsoryPolicyNo();
        this.trackingNum = workorderDTO.getTrackingNum();
        this.logisticsCompany = workorderDTO.getLogisticsCompany();
        this.acceptInsuranceRemark = workorderDTO.getAcceptInsuranceRemark();
        this.acceptInsuranceFailedRemark = workorderDTO.getAcceptInsuranceFailedRemark();
        this.createTime = workorderDTO.getCreateTime();
        this.updateTime = workorderDTO.getUpdateTime();
        this.quotationTime = workorderDTO.getQuotationTime();
        this.underwritingTime = workorderDTO.getUnderwritingTime();
        this.finishTime = workorderDTO.getFinishTime();
        this.createBy = workorderDTO.getCreateBy();
        this.handleBy = workorderDTO.getHandleBy();
        this.status = workorderDTO.getStatus();
        this.remindStatus = workorderDTO.getRemindStatus();
        this.followUpRes = workorderDTO.getFollowUpRes();
    }

    private BigDecimal percentageCompute(BigDecimal amount, BigDecimal percentage, Integer computeType){
        if(amount == null || percentage == null){
            return null;
        }
        if(computeType == 1){
            return amount.divide(new BigDecimal("1.06")).multiply(percentage.divide(new BigDecimal("100")));
        }
        return amount.multiply(percentage.divide(new BigDecimal("100")));
    }

    public void computeAmount(){
        if(upstreamCommercialAmount == null){
            upstreamCommercialAmount = percentageCompute(commercialAmount, upstreamCommercialPercentage, upstreamComputeType);
        }
        if(upstreamCompulsoryAmount == null){
            upstreamCompulsoryAmount = percentageCompute(compulsoryAmount, upstreamCompulsoryPercentage, upstreamComputeType);
        }
        if(upstreamVehicleAndVesselTaxAmount == null){
            upstreamVehicleAndVesselTaxAmount = percentageCompute(vehicleAndTaxAmount, upstreamVehicleAndVesselTaxPercentage, upstreamComputeType);
        }
        if(nonMotorAmount != null && upstreamNonMotorAmount == null){
            upstreamNonMotorAmount = percentageCompute(nonMotorAmount, upstreamNonMotorPercentage, upstreamComputeType);
        }
        if(downstreamCommercialAmount == null){
            downstreamCommercialAmount = percentageCompute(commercialAmount, downstreamCommercialPercentage, downstreamComputeType);
        }
        if(downstreamCompulsoryAmount == null){
            downstreamCompulsoryAmount = percentageCompute(compulsoryAmount, downstreamCompulsoryPercentage, downstreamComputeType);
        }
        if(downstreamVehicleAndVesselTaxAmount == null){
            downstreamVehicleAndVesselTaxAmount = percentageCompute(vehicleAndTaxAmount, downstreamVehicleAndVesselTaxPercentage, downstreamComputeType);
        }
        if(nonMotorAmount != null && downstreamNonMotorAmount == null){
            downstreamNonMotorAmount = percentageCompute(nonMotorAmount, downstreamNonMotorPercentage, downstreamComputeType);
        }
    }
}