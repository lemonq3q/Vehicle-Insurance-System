package com.example.insurancesystem.domain.workorder;

import com.alibaba.excel.annotation.ExcelProperty;
import com.example.insurancesystem.utils.TimeConvertUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
/**
 * 工单 Excel 导出行模型，将流程状态、客户车辆、上下游机构和金额字段转换为业务人员可读列。
 */
public class WorkorderExcelDTO {

    @ExcelProperty("工单号")
    private String code;

    @ExcelProperty("创建日期")
    private String createDate;

    @ExcelProperty("更新日期")
    private String updateDate;

    @ExcelProperty("承保日期")
    private String finishDate;

    @ExcelProperty("机构名称")
    private String organizationName;

    @ExcelProperty("提交店铺")
    private String createMerchant;

    @ExcelProperty("车辆类型")
    private String vehicleType;

    @ExcelProperty("牌照号")
    private String licensePlate;

    @ExcelProperty("车架号")
    private String vin;

    @ExcelProperty("车主")
    private String owner;

    @ExcelProperty("车主电话")
    private String ownerPhone;

    @ExcelProperty("商业险起保日期")
    private String commercialStartDate;

    @ExcelProperty("交强险起保日期")
    private String compulsoryStartDate;

    @ExcelProperty("投保类型")
    private String insureType;

    @ExcelProperty("商业保费(元)")
    private BigDecimal commercialPremium;

    @ExcelProperty("交强保费(元)")
    private BigDecimal compulsoryPremium;

    @ExcelProperty("车船税金额(元)")
    private BigDecimal vehicleVesselTax;

    @ExcelProperty("非车险金额（元）")
    private BigDecimal nonAutoInsuranceAmount;

    @ExcelProperty("上游计算方式")
    private String upstreamCalculateType;

    @ExcelProperty("上游商业险政策比例")
    private BigDecimal upstreamCommercialRatio;

    @ExcelProperty("上游商业险政策金额(元)")
    private BigDecimal upstreamCommercialAmount;

    @ExcelProperty("上游交强险政策比例")
    private BigDecimal upstreamCompulsoryRatio;

    @ExcelProperty("上游交强险政策金额(元)")
    private BigDecimal upstreamCompulsoryAmount;

    @ExcelProperty("上游车船税政策比例")
    private BigDecimal upstreamTaxRatio;

    @ExcelProperty("上游车船税政策金额(元)")
    private BigDecimal upstreamTaxAmount;

    @ExcelProperty("上游非车政策比例")
    private BigDecimal upstreamNonAutoRatio;

    @ExcelProperty("上游非车政策金额(元)")
    private BigDecimal upstreamNonAutoAmount;

    @ExcelProperty("上游政策金额(元)")
    private BigDecimal upstreamAmountSum;

    @ExcelProperty("下游计算方式")
    private String downstreamCalculateType;

    @ExcelProperty("下游商业险政策比例")
    private BigDecimal downstreamCommercialRatio;

    @ExcelProperty("下游商业险政策金额(元)")
    private BigDecimal downstreamCommercialAmount;

    @ExcelProperty("下游交强险政策比例")
    private BigDecimal downstreamCompulsoryRatio;

    @ExcelProperty("下游交强险政策金额(元)")
    private BigDecimal downstreamCompulsoryAmount;

    @ExcelProperty("下游车船税政策比例")
    private BigDecimal downstreamTaxRatio;

    @ExcelProperty("下游车船税政策金额(元)")
    private BigDecimal downstreamTaxAmount;

    @ExcelProperty("下游非车政策比例")
    private BigDecimal downstreamNonAutoRatio;

    @ExcelProperty("下游非车政策金额(元)")
    private BigDecimal downstreamNonAutoAmount;

    @ExcelProperty("下游政策金额(元)")
    private BigDecimal downstreamAmountSum;

    @ExcelProperty("处理人")
    private String handleUser;

    @ExcelProperty("收款人姓名")
    private String payeeName;

    @ExcelProperty("收款开户行")
    private String payeeBank;

    @ExcelProperty("收款账号")
    private String payeeAccount;

    @ExcelProperty("商业险保险公司")
    private String commercialInsurerName;

    @ExcelProperty("商业险保单号")
    private String commercialPolicyNo;

    @ExcelProperty("商业险终期")
    private String commercialInsuranceEndTime;

    @ExcelProperty("交强险保险公司")
    private String compulsoryInsurerName;

    @ExcelProperty("交强险保单号")
    private String compulsoryPolicyNo;

    @ExcelProperty("交强险终期")
    private String compulsoryInsuranceEndTime;

    @ExcelProperty("非车险保险公司")
    private String nonMotorInsurerName;

    @ExcelProperty("非车险保单号")
    private String nonMotorPolicyNo;

    @ExcelProperty("非车险终期")
    private String nonMotorInsuranceEndTime;

    @ExcelProperty("非车险起期")
    private String nonMotorInsuranceStartTime;

    @ExcelProperty("承保备注")
    private String insureRemark;

    /**
     * 将当前接口资料转换成导出行，三种保单分别展示公司、号码和完整保险期间。
     * 空时间不生成 1970 年日期；金额及上下游政策继续使用现有导出列，新增列不改旧列含义。
     * @param workorderDTO 已计算展示字段的本企业工单详情。
     */
    public WorkorderExcelDTO(WorkorderDTO workorderDTO) {
        this.commercialInsurerName = workorderDTO.getCommercialInsurerName();
        this.commercialPolicyNo = workorderDTO.getCommercialPolicyNo();
        this.commercialInsuranceEndTime = workorderDTO.getCommercialInsuranceEndTime() == null ? null : TimeConvertUtil.timestampConvert(workorderDTO.getCommercialInsuranceEndTime());
        this.compulsoryInsurerName = workorderDTO.getCompulsoryInsurerName();
        this.compulsoryPolicyNo = workorderDTO.getCompulsoryPolicyNo();
        this.compulsoryInsuranceEndTime = workorderDTO.getCompulsoryInsuranceEndTime() == null ? null : TimeConvertUtil.timestampConvert(workorderDTO.getCompulsoryInsuranceEndTime());
        this.nonMotorInsurerName = workorderDTO.getNonMotorInsurerName();
        this.nonMotorPolicyNo = workorderDTO.getNonMotorPolicyNo();
        this.nonMotorInsuranceEndTime = workorderDTO.getNonMotorInsuranceEndTime() == null ? null : TimeConvertUtil.timestampConvert(workorderDTO.getNonMotorInsuranceEndTime());
        this.nonMotorInsuranceStartTime = workorderDTO.getNonMotorInsuranceStartTime() == null ? null : TimeConvertUtil.timestampConvert(workorderDTO.getNonMotorInsuranceStartTime());
        this.code = workorderDTO.getCode();

        // 时间处理
        if (workorderDTO.getCreateTime() != null) {
            this.createDate = TimeConvertUtil.timestampConvert(workorderDTO.getCreateTime());
        }
        if (workorderDTO.getUpdateTime() != null) {
            this.updateDate = TimeConvertUtil.timestampConvert(workorderDTO.getUpdateTime());
        }
        if (workorderDTO.getFinishTime() != null) {
            this.finishDate = TimeConvertUtil.timestampConvert(workorderDTO.getFinishTime());
        }

        this.organizationName = workorderDTO.getInsuranceMerchantName();
        this.createMerchant = workorderDTO.getCreateMerchantName();

        if (workorderDTO.getVehicleLicense() != null) {
            this.vehicleType = workorderDTO.getVehicleLicense().getVehicleType();
        }
        if (this.vehicleType == null || this.vehicleType.isBlank()) {
            this.vehicleType = workorderDTO.getVehicleInvoice() != null ? workorderDTO.getVehicleInvoice().getVehicleType() : "";
        }
        if (this.vehicleType == null || this.vehicleType.isBlank()) {
            this.vehicleType = workorderDTO.getVehicleCertificate() != null ? workorderDTO.getVehicleCertificate().getVehicleType() : "";
        }

        if (workorderDTO.getVehicleLicense() != null) {
            this.licensePlate = workorderDTO.getVehicleLicense().getLicensePlate();
        }

        // 车架号
        if (workorderDTO.getVehicleCertificate() != null) {
            this.vin = workorderDTO.getVehicleCertificate().getVehicleCode();
        }
        if ((this.vin == null || this.vin.isEmpty()) && workorderDTO.getVehicleInvoice() != null) {
            this.vin = workorderDTO.getVehicleInvoice().getVehicleCode();
        }
        if ((this.vin == null || this.vin.isEmpty()) && workorderDTO.getVehicleLicense() != null) {
            this.vin = workorderDTO.getVehicleLicense().getVehicleCode();
        }

        if(workorderDTO.getOwnerName() != null && !workorderDTO.getOwnerName().isEmpty()){
            this.owner = workorderDTO.getOwnerName();
        }
        else {
            this.owner = workorderDTO.getOrganizationName();
        }

        this.ownerPhone = workorderDTO.getOwnerPhone();

        // 起保日期（修复原bug：交强险日期被覆盖）
        if (workorderDTO.getCommercialInsuranceStartTime() != null) {
            this.commercialStartDate = TimeConvertUtil.timestampConvert(workorderDTO.getCommercialInsuranceStartTime());
        }
        if (workorderDTO.getCompulsoryInsuranceStartTime() != null) {
            this.compulsoryStartDate = TimeConvertUtil.timestampConvert(workorderDTO.getCompulsoryInsuranceStartTime());
        }

        // ===================== 金额类：null → 0 =====================
        this.commercialPremium = nullToZero(workorderDTO.getCommercialAmount());
        this.compulsoryPremium = nullToZero(workorderDTO.getCompulsoryAmount());
        this.vehicleVesselTax = nullToZero(workorderDTO.getVehicleAndTaxAmount());
        this.nonAutoInsuranceAmount = nullToZero(workorderDTO.getNonMotorAmount());

        // 上游金额
        this.upstreamCommercialAmount = nullToZero(workorderDTO.getUpstreamCommercialAmount());
        this.upstreamCompulsoryAmount = nullToZero(workorderDTO.getUpstreamCompulsoryAmount());
        this.upstreamTaxAmount = nullToZero(workorderDTO.getUpstreamVehicleAndVesselTaxAmount());
        this.upstreamNonAutoAmount = nullToZero(workorderDTO.getUpstreamNonMotorAmount());
        this.upstreamAmountSum = nullToZero(workorderDTO.getUpstreamSumAmount());

        // 下游金额
        this.downstreamCommercialAmount = nullToZero(workorderDTO.getDownstreamCommercialAmount());
        this.downstreamCompulsoryAmount = nullToZero(workorderDTO.getDownstreamCompulsoryAmount());
        this.downstreamTaxAmount = nullToZero(workorderDTO.getDownstreamVehicleAndVesselTaxAmount());
        this.downstreamNonAutoAmount = nullToZero(workorderDTO.getDownstreamNonMotorAmount());
        this.downstreamAmountSum = nullToZero(workorderDTO.getDownstreamSumAmount());

        // ===================== 比例类：0 → null =====================
        this.upstreamCommercialRatio = zeroToNull(workorderDTO.getUpstreamCommercialPercentage());
        this.upstreamCompulsoryRatio = zeroToNull(workorderDTO.getUpstreamCompulsoryPercentage());
        this.upstreamTaxRatio = zeroToNull(workorderDTO.getUpstreamVehicleAndVesselTaxPercentage());
        this.upstreamNonAutoRatio = zeroToNull(workorderDTO.getUpstreamNonMotorPercentage());

        this.downstreamCommercialRatio = zeroToNull(workorderDTO.getDownstreamCommercialPercentage());
        this.downstreamCompulsoryRatio = zeroToNull(workorderDTO.getDownstreamCompulsoryPercentage());
        this.downstreamTaxRatio = zeroToNull(workorderDTO.getDownstreamVehicleAndVesselTaxPercentage());
        this.downstreamNonAutoRatio = zeroToNull(workorderDTO.getDownstreamNonMotorPercentage());

        // 计算方式
        this.upstreamCalculateType = Integer.valueOf(1).equals(workorderDTO.getUpstreamComputeType()) ? "税后" : "税前";
        this.downstreamCalculateType = Integer.valueOf(1).equals(workorderDTO.getDownstreamComputeType()) ? "税后" : "税前";

        // 其他信息
        this.handleUser = workorderDTO.getHandleUserName();
        this.payeeName = workorderDTO.getPayName();
        this.payeeBank = workorderDTO.getPayBank();
        this.payeeAccount = workorderDTO.getPayBankCardNum();
        this.insureRemark = workorderDTO.getAcceptInsuranceRemark();
    }

    /**
     * 金额工具方法：null 转为 0
     */
    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * 比例工具方法：0 转为 null
     */
    private BigDecimal zeroToNull(BigDecimal value) {
        return value != null && BigDecimal.ZERO.compareTo(value) == 0 ? null : value;
    }
}
