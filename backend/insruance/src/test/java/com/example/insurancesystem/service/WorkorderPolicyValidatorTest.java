package com.example.insurancesystem.service;

import com.example.insurancesystem.domain.workorder.Workorder;
import com.example.insurancesystem.domain.workorder.WorkorderDTO;
import com.example.insurancesystem.domain.workorder.WorkorderExcelDTO;
import com.example.insurancesystem.domain.workorder.WorkorderFile;
import com.example.insurancesystem.handler.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证真实保单校验和费用规则，覆盖取消险种、零元、税口径、历史支付及导出空值。
 * 测试不连接数据库；每个请求使用独立快照，避免跨用例共享状态。
 */
class WorkorderPolicyValidatorTest {
    /**
     * 建立任意选中险种的完整请求，每类金额100.05、上下游比例10%，便于核对去税舍入。
     * @param types 本次完整险种选择。
     * @return 含对应附件的可提交请求。
     */
    static WorkorderDTO request(String... types) {
        WorkorderDTO request = new WorkorderDTO();
        request.setId(101L); request.setPolicyTypes(List.of(types));
        request.setUpstreamComputeType(1); request.setDownstreamComputeType(0);
        List<WorkorderFile> files = new ArrayList<>();
        BeanWrapper model = new BeanWrapperImpl(request);
        for (String type : types) {
            String prefix = switch (type) { case "COMMERCIAL" -> "commercial"; case "COMPULSORY" -> "compulsory"; default -> "nonMotor"; };
            model.setPropertyValue(prefix + "InsurerName", "测试公司");
            model.setPropertyValue(prefix + "PolicyNo", "POL-" + type);
            model.setPropertyValue(prefix + "InsuranceStartTime", 1793462400L);
            model.setPropertyValue(prefix + "InsuranceEndTime", 1824998400L);
            model.setPropertyValue(prefix + "Amount", new BigDecimal("100.05"));
            WorkorderFile file = new WorkorderFile(); file.setFileId(900L + files.size());
            file.setType("NON_MOTOR".equals(type) ? "acceptInsuranceOther"
                    : "acceptInsurance" + prefix.substring(0, 1).toUpperCase() + prefix.substring(1));
            files.add(file);
            for (String side : List.of("upstream", "downstream")) {
                model.setPropertyValue(side + prefix.substring(0,1).toUpperCase() + prefix.substring(1) + "Percentage", new BigDecimal("10"));
            }
        }
        request.setVehicleAndTaxAmount(new BigDecimal("360"));
        request.setWorkorderFileList(files);
        return request;
    }

    /**

     * 三类保费、零车船税与政策均按精确十进制运算，支付历史金额不属于承保白名单。

     */
    @Test
    void normalizesThreePoliciesAndPreservesOriginalPolicyFormula() {
        WorkorderDTO request = request("COMMERCIAL", "COMPULSORY", "NON_MOTOR");
        request.setRequiredPayAmount(new BigDecimal("9999"));
        request.setVehicleAndTaxAmount(BigDecimal.ZERO);
        Workorder normalized = WorkorderPolicyValidator.normalize(request);
        assertEquals(new BigDecimal("9.44"), normalized.getUpstreamCommercialAmount());
        assertEquals(new BigDecimal("10.01"), normalized.getDownstreamCommercialAmount());
        assertEquals(BigDecimal.ZERO, normalized.getVehicleAndTaxAmount());
        assertNull(normalized.getRequiredPayAmount());
        assertNull(normalized.getQuotationRemark());
        assertEquals("POL-NON_MOTOR", normalized.getNonMotorPolicyNo());
    }

    /**

     * 取消交强/非车险应清空旧资料及政策；金额模式的明确0优先于任何重算。

     */
    @Test
    void cancellationAndManualZeroCannotRetainOldValues() {
        WorkorderDTO request = request("COMMERCIAL");
        request.setCompulsoryPolicyNo("OLD");
        request.setCompulsoryAmount(BigDecimal.TEN);
        request.setUpstreamCompulsoryAmount(BigDecimal.TEN);
        request.setUpstreamCommercialPercentage(null);
        request.setUpstreamCommercialAmount(BigDecimal.ZERO);
        Workorder normalized = WorkorderPolicyValidator.normalize(request);
        assertNull(normalized.getCompulsoryPolicyNo());
        assertNull(normalized.getCompulsoryAmount());
        assertNull(normalized.getVehicleAndTaxAmount());
        assertNull(normalized.getNonMotorInsuranceStartTime());
        assertNull(normalized.getUpstreamCompulsoryAmount());
        assertEquals(new BigDecimal("0.00"), normalized.getUpstreamCommercialAmount());
        assertNull(normalized.getUpstreamCommercialPercentage());
    }

    /**

     * 附件可缺省；倒置期间、非法金额及同时提交金额和比例仍应拒绝。

     */
    @Test
    void rejectsInvalidPoliciesAndAmbiguousCommissionModes() {
        WorkorderDTO missing = request("COMMERCIAL"); missing.setWorkorderFileList(List.of());
        assertNotNull(WorkorderPolicyValidator.normalize(missing));
        WorkorderDTO reversed = request("COMMERCIAL"); reversed.setCommercialInsuranceEndTime(reversed.getCommercialInsuranceStartTime());
        assertThrows(BusinessException.class, () -> WorkorderPolicyValidator.normalize(reversed));
        WorkorderDTO duplicate = request("COMMERCIAL"); duplicate.setPolicyTypes(List.of("COMMERCIAL", "COMMERCIAL"));
        assertThrows(BusinessException.class, () -> WorkorderPolicyValidator.normalize(duplicate));
        WorkorderDTO money = request("COMMERCIAL"); money.setCommercialAmount(new BigDecimal("1.001"));
        assertThrows(BusinessException.class, () -> WorkorderPolicyValidator.normalize(money));
        WorkorderDTO mixed = request("COMMERCIAL"); mixed.setUpstreamCommercialAmount(BigDecimal.ONE);
        assertThrows(BusinessException.class, () -> WorkorderPolicyValidator.normalize(mixed));
    }

    /**
     * 三类保单只有起期和保费必填；终期、公司名称、号码、附件及交强车船税可空。
     * 空车船税保持 NULL，明确填写零元仍保留零元语义。
     */
    @Test
    void optionalPolicyMetadataAndFilesRemainEmpty() {
        WorkorderDTO request = request("COMMERCIAL", "COMPULSORY", "NON_MOTOR");
        request.setCommercialInsurerName(null); request.setCommercialPolicyNo(null);
        request.setCompulsoryInsurerName(""); request.setCompulsoryPolicyNo("");
        request.setNonMotorInsurerName(null); request.setNonMotorPolicyNo(null);
        request.setCommercialInsuranceEndTime(null);
        request.setCompulsoryInsuranceEndTime(null);
        request.setNonMotorInsuranceEndTime(null);
        request.setVehicleAndTaxAmount(null);
        request.setWorkorderFileList(List.of());
        Workorder normalized = WorkorderPolicyValidator.normalize(request);
        assertNull(normalized.getCommercialInsurerName());
        assertNull(normalized.getCommercialPolicyNo());
        assertNull(normalized.getCommercialInsuranceEndTime());
        assertNull(normalized.getCompulsoryInsuranceEndTime());
        assertNull(normalized.getNonMotorInsuranceEndTime());
        assertNull(normalized.getVehicleAndTaxAmount());
        request.setCommercialAmount(null);
        assertThrows(BusinessException.class, () -> WorkorderPolicyValidator.normalize(request));
    }

    /**
     * 非车险与其他单证共用附件类型，允许一至六份；取消非车险后不能继续携带附件。
     * 该校验发生在写入前，超限和错选均不改变工单或文件关系。
     */
    @Test
    void nonMotorDocumentsUseOneSharedMultiFileGroup() {
        WorkorderDTO request = request("NON_MOTOR");
        WorkorderFile second = new WorkorderFile(); second.setFileId(901L); second.setType("acceptInsuranceOther");
        request.setWorkorderFileList(new ArrayList<>(request.getWorkorderFileList()));
        request.getWorkorderFileList().add(second);
        assertNotNull(WorkorderPolicyValidator.normalize(request));
        for (long id = 902; id <= 905; id++) {
            WorkorderFile file = new WorkorderFile(); file.setFileId(id); file.setType("acceptInsuranceOther");
            request.getWorkorderFileList().add(file);
        }
        assertNotNull(WorkorderPolicyValidator.normalize(request));
        WorkorderFile seventh = new WorkorderFile(); seventh.setFileId(906L); seventh.setType("acceptInsuranceOther");
        request.getWorkorderFileList().add(seventh);
        assertThrows(BusinessException.class, () -> WorkorderPolicyValidator.normalize(request));
        request.setPolicyTypes(List.of("COMMERCIAL"));
        assertThrows(BusinessException.class, () -> WorkorderPolicyValidator.normalize(request));
    }

    /**
     * 其他单证属于独立附件组，未选择非车险也允许记录；第七份必须在写入前被拒绝。
     * 商业险仍维持原有的单份保单限制，避免新附件类型改变原险种规则。
     */
    @Test
    void miscellaneousDocumentsAreIndependentOfNonMotor() {
        WorkorderDTO request = request("COMMERCIAL");
        request.setWorkorderFileList(new ArrayList<>(request.getWorkorderFileList()));
        for (long id = 920; id <= 925; id++) {
            WorkorderFile file = new WorkorderFile(); file.setFileId(id); file.setType("acceptInsuranceMisc");
            request.getWorkorderFileList().add(file);
        }
        assertNotNull(WorkorderPolicyValidator.normalize(request));
        WorkorderFile seventh = new WorkorderFile(); seventh.setFileId(926L); seventh.setType("acceptInsuranceMisc");
        request.getWorkorderFileList().add(seventh);
        assertThrows(BusinessException.class, () -> WorkorderPolicyValidator.normalize(request));
    }

    /**

     * 三保费之和不含车船税，派生金额不改变支付历史；历史空计算方式导出不能空指针。

     */
    @Test
    void totalsAndExportSeparatePremiumFromTaxAndAcceptHistoricalNulls() {
        WorkorderDTO row = new WorkorderDTO();
        row.computePolicyTotals(); assertNull(row.getTotalPremiumAmount());
        row.setCommercialAmount(new BigDecimal("3000"));
        row.setCompulsoryAmount(new BigDecimal("950"));
        row.setNonMotorAmount(new BigDecimal("200"));
        row.setVehicleAndTaxAmount(new BigDecimal("360"));
        row.setRequiredPayAmount(new BigDecimal("9999"));
        row.setUpstreamVehicleAndVesselTaxAmount(new BigDecimal("12.34"));
        row.setDownstreamVehicleAndVesselTaxAmount(new BigDecimal("56.78"));
        row.setNonMotorInsuranceStartTime(1793462400L);
        row.setNonMotorPolicyNo("N001");
        row.computePolicyTotals();
        assertEquals(new BigDecimal("4150"), row.getTotalPremiumAmount());
        assertEquals(new BigDecimal("4510"), row.getPayableAmount());
        assertEquals(new BigDecimal("9999"), row.getRequiredPayAmount());
        WorkorderExcelDTO excel = new WorkorderExcelDTO(row);
        assertEquals("N001", excel.getNonMotorPolicyNo());
        assertNotNull(excel.getNonMotorInsuranceStartTime());
        assertEquals(new BigDecimal("12.34"), excel.getUpstreamTaxAmount());
        assertEquals("税前", excel.getUpstreamCalculateType());
    }
}

